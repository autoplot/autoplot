/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.jythonsupport;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.Py;
import org.python.core.PyException;
import org.python.core.PyFloat;
import org.python.core.PyInteger;
import org.python.core.PyJavaInstance;
import org.python.core.PyList;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PySyntaxError;
import org.python.core.PySystemState;
import org.python.parser.SimpleNode;
import org.python.parser.ast.Assign;
import org.python.parser.ast.Attribute;
import org.python.parser.ast.Call;
import org.python.parser.ast.If;
import org.python.parser.ast.Module;
import org.python.parser.ast.Name;
import org.python.parser.ast.Subscript;
import org.python.parser.ast.VisitorBase;
import org.python.parser.ast.exprType;
import org.python.parser.ast.stmtType;
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
     * This also adds things to the python search path 
     * (see getLocalJythonAutoplotLib) so imports will find them.
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
                InputStream in = imports.openStream();
                try {
                    interp.execfile(imports.openStream(), "imports.py");
                } finally {
                    in.close();
                }
            }
        }


        return interp;

    }
    
    /**
     * set up the interp variables scripts will often use, such as PWD and monitor.
     * @param interp
     * @param pwd
     * @param resourceUri
     * @param paramsl
     * @param mon 
     */
    public static void setupInterp( PythonInterpreter interp, String pwd, String resourceUri, Map<String,String> paramsl, ProgressMonitor mon ) {
        interp.set("PWD", pwd);
        interp.exec("import autoplot");
        interp.exec("autoplot.params=dict()");
        for ( Entry<String,String> e : paramsl.entrySet()) {
            String s= e.getKey();
            if (!s.equals("arg_0") && !s.equals("script") ) {
                String sval= e.getValue();
                sval= JythonUtil.maybeQuoteString( sval );
                logger.log(Level.FINE, "autoplot.params[''{0}'']={1}", new Object[]{s, sval});
                interp.exec("autoplot.params['" + s + "']=" + sval);
            }
        }

        if ( resourceUri!=null ) {
            interp.set( "resourceURI", resourceUri ); // legacy
            interp.exec("autoplot.params['"+"resourceURI"+"']="+ JythonUtil.maybeQuoteString( resourceUri ) );
        }
        
        interp.set("monitor", mon);
        
        try {
            InputStream in= JythonOps.class.getResource("imports.py").openStream();
            try {
                interp.execfile( in, "imports.py"); // import everything into default namespace.
            } finally {
                in.close();
            }
        } catch ( IOException ex ) {
            logger.log( Level.SEVERE, ex.getMessage(), ex );
        }
        
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
                if ( new File( ff3, s ).setReadOnly()==false ) {
                    logger.log( Level.FINER, "set read-only on file {0} failed", s );
                }
                if ( new File( ff3, s ).setWritable( true, true )==false ) {
                    logger.log( Level.FINER, "set write for user only on file {0} failed", s );
                }
            }
            s= r.readLine();
        }
        r.close();
        logger.fine("   ...done");
        return ff3.toString();
    }
        
    /**
     * copy the two python files specific to Autoplot into the user's autoplot_data/jython folder.
     * This reads the version from the first line of the autoplot.py.
     * @return the item to add to the python search path.
     * @throws IOException 
     */
    private static String getLocalJythonAutoplotLib() throws IOException {
        File ff2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA ) );
        File ff3= new File( ff2.toString() + "/jython" );
        File ff4= new File( ff3, "autoplot.py" );
        String vers= "";
        
        double currentVersion= 1.11;  //rfe320 improved getParam support.
                
        if ( ff4.exists() ) {
            BufferedReader r= new BufferedReader( new FileReader( ff4 ) );
            try {
                String line= r.readLine();
                if ( line!=null ) {
                    Pattern versPattern= Pattern.compile("# autoplot.py v([\\d\\.]+) .*");  // must be parsable as a double.
                    Matcher m= versPattern.matcher(line);
                    if ( m.matches() ) {
                        vers= m.group(1);
                    }
                }
            } finally {
                r.close();
            }
        }
        
        if ( ! ff4.exists() || vers.equals("") || Double.parseDouble(vers)<currentVersion ) {
            logger.log(Level.FINE, "looking for version={0} of {1}, but didn''t find it.", new Object[] { currentVersion, ff4 } );
            logger.log(Level.FINE, "doesn't seem like we have the right file, downloading...");
            if ( !ff3.exists() ) {
                if ( !ff3.mkdir() ) {
                    throw new IOException("Unable to mkdir "+ff3);
                }
            }
            String[] ss= new String[] { "autoplot.py", "autoplotapp.py" };
            for ( String s: ss ) {
                InputStream in= JythonUtil.class.getResourceAsStream("/"+s);
                FileOutputStream out= new FileOutputStream( new File( ff3, s ) );
                try {
                    transferStream(in,out);
                } finally {
                    out.close();
                    in.close();
                }
                if ( new File( ff3, s ).setReadOnly()==false ) {
                    logger.log( Level.FINER, "set read-only on file {0} failed", s );
                }
                if ( new File( ff3, s ).setWritable( true, true )==false ) {
                    logger.log( Level.FINER, "set write for user only on file {0} failed", s );
                }
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
        @Override
        public String toString() {
            return name+"="+deft;
        }
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
     
     //there are a number of functions which take a trivial amount of time to execute and are needed for some scripts, such as the string.upper() function.
     //The commas are to guard against the id being a subset of another id ("lower," does not match "lowercase").
     private static String[] okay= new String[] { "range,", "xrange,", "getParam,", "lower,", "upper,", "URI," };
     
     /**
      * return true if the function call is trivial to execute and can be evaluated within a few milliseconds.
      * @param sn
      * @return 
      */
     private static boolean trivialFunctionCall( SimpleNode sn ) {
         if ( sn instanceof Call ) {
             Call c= (Call)sn;
             boolean klugdyOkay= false;
             for ( String s: okay ) {
                if ( c.func.toString().contains(s) ) klugdyOkay= true;
             }
             return klugdyOkay;
         } else {
             return false;
         }
     }
             
     private static class MyVisitorBase<R> extends VisitorBase {
         boolean looksOkay= true; 
         boolean visitNameFail= false;
         
         HashSet names= new HashSet();
         MyVisitorBase( HashSet names ) {
             this.names= names;
         }

        @Override
        public Object visitName(Name node) throws Exception {
            if ( !names.contains(node.id) ) {
                visitNameFail= true;
            }
            return super.visitName(node); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public Object visitCall(Call node) throws Exception {
            return super.visitCall(node); //To change body of generated methods, choose Tools | Templates.
        }
        
         @Override
         protected Object unhandled_node(SimpleNode sn) throws Exception {
             return sn;
         }
         @Override
         public void traverse(SimpleNode sn) throws Exception {
             if ( sn instanceof Call ) {
                 looksOkay= trivialFunctionCall(sn);
             } else if ( sn instanceof Assign ) { // TODO: I have to admit I don't understand what traverse means.  I would have thought it was all nodes...
                 Assign a= ((Assign)sn);
                 exprType et= a.value;
                 if ( et instanceof Call ) {
                     looksOkay= trivialFunctionCall(et);
                 }
             } else if ( sn instanceof Name ) {
                 //visitName((Name)sn).id
             }
         }
         public boolean looksOkay() {
             return looksOkay;
         }
         /**
          * this contains a node whose name we can't resolve.
          * @return 
          */
         public boolean visitNameFail() {
             return visitNameFail;
         }
     }
     
     /**
      * inspect the node to look for function calls that are not to the function "getParam".  This is awful code that 
      * will be rewritten when we upgrade Python to 2.7.
      * @param o
      * @param variableNames
      * @return 
      */
     private static boolean simplifyScriptToGetParamsOkayNoCalls( SimpleNode o, HashSet<String> variableNames ) {
        
        if ( o instanceof Call ) { 
            Call c= (Call)o;

            if ( !trivialFunctionCall(c) ) {
                logger.finest( String.format( "%04d simplify->false: %s", o.beginLine, o.toString() ) );
                return false;
            }
        }
        MyVisitorBase vb= new MyVisitorBase(variableNames);
        try {
            o.traverse(vb);
            logger.finest( String.format( " %04d simplify->%s: %s", o.beginLine, vb.looksOkay(), o ) );
            return vb.looksOkay();
            
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        logger.finest( String.format( "!! %04d simplify->false: %s", o.beginLine, o ) );
         return false;
     }
     
     /**
      * can we resolve this node given the variable names we know?
      * @param o
      * @param variableNames
      * @return true if the node can be resolved.
      */
     private static boolean simplifyScriptToGetParamsCanResolve( SimpleNode o, HashSet<String> variableNames ) {
        //if ( o.beginLine>=617 && o.beginLine<619 ) {
        //    System.err.println( "here at 617-ish");
        //}
        if ( o instanceof Name ) { 
            Name c= (Name)o;
            if ( !variableNames.contains( c.id ) ) {
                logger.finest( String.format( "%04d canResolve->false: %s", o.beginLine, o.toString() ) );
                return false;
            }
        }
        if ( o instanceof Attribute ) {
            Attribute at= (Attribute)o;
            while ( at.value instanceof Attribute || at.value instanceof Subscript ) {
                if ( at.value instanceof Attribute ) {
                    at= (Attribute)at.value;
                } else {
                    Subscript s= (Subscript)at.value;
                    if ( s.value instanceof Attribute ) {
                        at= (Attribute)s.value;
                    } else {
                        return false; // oh just give up...
                    }
                }
            }
            if ( at.value instanceof Name ) {
                Name n= (Name)at.value;
                if ( !variableNames.contains( n.id ) ) return false;
            }
        }
        MyVisitorBase vb= new MyVisitorBase(variableNames);
        try {
            o.traverse(vb);
            logger.finest( String.format( " %04d canResolve->%s: %s", o.beginLine,  vb.visitNameFail, o ) );
            return !vb.visitNameFail;
            
        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        logger.finest( String.format( "!! %04d canResolve->false: %s", o.beginLine, o ) );
         return false;
     }     

     
     public static String maybeQuoteString(String sval) {
        boolean isNumber= false;
        try {
            Double.parseDouble(sval); 
        } catch ( NumberFormatException ex ) {
            isNumber= false;
        }

        if ( sval.length()>0 && !isNumber && !sval.equals("True") && !sval.equals("False") ) {
            if ( !( sval.startsWith("'") && sval.endsWith("'") ) ) {
                sval= String.format( "'%s'", sval );
            }
        }
        return sval;

    }

    public static void setParams( PythonInterpreter interp, Map<String,String> paramsl ) {
        interp.exec("import autoplot");
        interp.exec("autoplot.params=dict()");
        for ( Entry<String,String> e : paramsl.entrySet()) {
            String s= e.getKey();
            if (!s.equals("arg_0") && !s.equals("script") ) {
                String sval= e.getValue();
                
                sval= maybeQuoteString( sval );
                logger.log(Level.FINE, "autoplot.params[''{0}'']={1}", new Object[]{s, sval});
                interp.exec("autoplot.params['" + s + "']=" + sval);
            }
        }
        //interp.eval("autoplot.params");
    }

     
     /**
      * return true if we can include this in the script without a huge performance penalty.
      * @param o
      * @return 
      */
     private static boolean simplifyScriptToGetParamsOkay( stmtType o, HashSet<String> variableNames ) {
         //if ( o.beginLine==607 ) {
         //    System.err.println("here at line "+o.beginLine);
         //}
         if ( ( o instanceof org.python.parser.ast.ImportFrom ) ) return true;
         if ( ( o instanceof org.python.parser.ast.Import ) ) return true;
         if ( ( o instanceof org.python.parser.ast.Assign ) ) {
             Assign a= (Assign)o;
             if ( simplifyScriptToGetParamsOkayNoCalls( a.value, variableNames ) ) {
                 if ( !simplifyScriptToGetParamsCanResolve(a.value, variableNames ) ) {
                     return false;
                 }
                 for ( int i=0; i<a.targets.length; i++ ) {
                    exprType et= ((exprType)a.targets[i]);
                    if ( et instanceof Name ) {
                        String id= ((Name)a.targets[i]).id;
                        variableNames.add(id);
                        logger.log(Level.FINEST, "assign to variable {0}", id);
                    } else if ( et instanceof Attribute ) {
                        Attribute at= (Attribute)et;
                        while ( at.value instanceof Attribute || at.value instanceof Subscript ) {
                            if ( at.value instanceof Attribute ) {
                                at= (Attribute)at.value;
                            } else {
                                Subscript s= (Subscript)at.value;
                                if ( s.value instanceof Attribute ) {
                                    at= (Attribute)s.value;
                                } else {
                                    return false; // oh just give up...
                                }
                            }
                        }
                        if ( at.value instanceof Name ) {
                            Name n= (Name)at.value;
                            if ( !variableNames.contains( n.id ) ) return false;
                        }
                    }
                 }
                 return true;
             } else {
                 return false;
             }
         }
         if ( ( o instanceof org.python.parser.ast.If ) )  return simplifyScriptToGetParamsOkayNoCalls(o,variableNames);
         if ( ( o instanceof org.python.parser.ast.Print ) ) return simplifyScriptToGetParamsOkayNoCalls(o,variableNames);
         logger.log( Level.FINEST, "not okay to simplify: {0}", o);
         return false;
     }
     
     private static void maybeAppendSort( String theLine, StringBuilder result ) {
         int i= theLine.indexOf("getParam");
         if ( i!=-1 ) {
             i= theLine.indexOf("=");
             String v= theLine.substring(0,i).trim();
             int indent= theLine.indexOf(v);
             if ( indent>0 ) result.append( theLine.substring(0,indent) );
             result.append("sort_.append( \'").append(v).append( "\')\n");
         }
     }
     
     private static StringBuilder appendToResult( StringBuilder result, String line ) {
         //if ( line.contains("getDataSet") ) {
         //    System.err.println("here626");
         //}
         result.append(line);
         return result;
     }
     
     /**
      * Extracts the parts of the program that get parameters or take a trivial amount of time to execute.  
      * This may call itself recursively when if blocks are encountered.
      * See test038.
      * @param ss the entire script.
      * @param stmts statements being processed.
      * @param variableNames variable names that have been resolved.
      * @param beginLine first line of the script being processed.
      * @param lastLine INCLUSIVE last line of the script being processed.
      * @param depth recursion depth, for debugging.
      * @return 
      */
     public static String simplifyScriptToGetParams( String[] ss, stmtType[] stmts, HashSet variableNames, int beginLine, int lastLine, int depth  ) {
         int acceptLine= -1;  // first line to accept
         StringBuilder result= new StringBuilder();
         for ( int istatement=0; istatement<stmts.length; istatement++ ) {
             stmtType o= stmts[istatement];
             logger.log( Level.FINER, "line {0}: {1}", new Object[] { o.beginLine, o.beginLine>0 ? ss[o.beginLine-1] : "(bad line number)" } );
             if ( o.beginLine>0 ) {
                 beginLine= o.beginLine;
             } else {
                 acceptLine= beginLine; // elif clause in autoplot-test038/lastSuccessfulBuild/artifact/test038_demoParms1.jy
             }
             if ( beginLine>lastLine ) {
                 continue;
             }
             if ( o instanceof org.python.parser.ast.If ) {
                 if ( acceptLine>-1 ) {
                    for ( int i=acceptLine; i<beginLine; i++ ) {
                        appendToResult( result,ss[i-1]).append("\n");
                    }
                 }
                 If iff= (If)o;
                 for ( int i=beginLine; i<iff.body[0].beginLine; i++ ) result.append(ss[i-1]).append("\n"); // write out the 'if' part
                 int lastLine1;  //lastLine1 is the last line of the "if" clause.
                 if ( iff.orelse!=null && iff.orelse.length>0 ) {
                     if ( iff.orelse[0].beginLine>0 ) {
                         lastLine1= iff.orelse[0].beginLine-2;  // -2 is for the "else:" part.
                     } else {
                         if ( iff.orelse[0] instanceof If ) {
                             lastLine1= ((If)iff.orelse[0]).test.beginLine-1;
                         } else {
                             logger.warning("failure to deal with another day...");
                             throw new RuntimeException("this case needs to be dealt with...");
                         }
                     }
                 } else if ( (istatement+1)<stmts.length ) {
                     lastLine1= stmts[istatement+1].beginLine-1;
                 } else {
                     lastLine1= lastLine;
                 }
                 String ss1= simplifyScriptToGetParams( ss, iff.body, variableNames, -1, lastLine1, depth+1 );
                 if ( ss1.length()==0 ) {
                     String line;
                     if ( iff.beginLine==0 && beginLine>0 && iff.body[0].beginLine>0 ) {
                        line= ss[iff.body[0].beginLine-1]; 
                     } else {
                        line= ss[iff.beginLine];
                     }
                     String[] ss2= line.split("\\S",-2);
                     String indent= ss2[0];
                     result.append(indent).append("pass\n");  
                     logger.fine("things have probably gone wrong...");
                 } else {
                     appendToResult( result,ss1);
                 }
                 if ( iff.orelse!=null ) {
                     appendToResult( result,ss[lastLine1] );
                     int lastLine2;
                     if ( (istatement+1)<stmts.length ) {
                        lastLine2= stmts[istatement+1].beginLine-1;
                     } else {
                        lastLine2= lastLine;
                     }
                     String ss2= simplifyScriptToGetParams( ss, iff.orelse, variableNames, lastLine1+2, lastLine2, depth+1 );
                     if ( ss2.length()>0 ) {
                         result.append("\n");
                     }
                     appendToResult( result,ss2);
                     if ( ss2.length()==0  ) { // we didn't add anything...
                         String line;
                         line= ss[iff.orelse[0].beginLine-1];
                         String[] ss3= line.split("\\S",-2);
                         String indent= ss3[0];
                         result.append("\n").append(indent).append("pass\n");  
                     } else {
                         result.append("\n");  // write of the else or elif line
                     }
                 }
                 acceptLine= -1;
             } else {
                 if ( simplifyScriptToGetParamsOkay( o, variableNames ) ) {
                     if ( acceptLine<0 ) acceptLine= (o).beginLine;
                 } else {
                     if ( acceptLine>-1 ) {
                         int thisLine= (o).beginLine;
                         for ( int i=acceptLine; i<thisLine; i++ ) {
                             appendToResult(result,ss[i-1]).append("\n");
                         }
                         acceptLine= -1;
                     }
                }
             }
         }
         if ( acceptLine>-1 ) {
             int thisLine= lastLine;
             for ( int i=acceptLine; i<=thisLine; i++ ) {
                 appendToResult( result,ss[i-1]).append("\n");
             }
         }
         return result.toString();         
     }
     
     /**
     * extracts the parts of the program that get parameters.  
     *
     * @param script the entire python program
     * @param addSort if true, add parameters to keep track of the order that getParam was called.  This has no effect now.
     * @return the python program with lengthy calls removed, up to the last getParam call.
     */
    
     public static String simplifyScriptToGetParams( String script, boolean addSort) throws PySyntaxError {
         String[] ss= script.split("\n");
         
         int lastLine= -1;
         for ( int i=0; i<ss.length; i++ ) {
             if ( ss[i].contains("getParam(") ) lastLine= i+1;
         }
         
         if ( lastLine==-1 ) {
             return "";
         }
         
         // check for continuation in last getParam call.
         while ( ss[lastLine].trim().length()>0 && Character.isWhitespace( ss[lastLine].charAt(0) ) && ss.length>lastLine+1 ) {
             lastLine++;
         }
         
         HashSet variableNames= new HashSet();
         variableNames.add("getParam");
         try {
             Module n= (Module)org.python.core.parser.parse( script, "exec" );
             return simplifyScriptToGetParams( ss, n.body, variableNames, 1, lastLine, 0 );
         } catch ( PySyntaxError ex ) {
             throw ex;
         }
     }
          
     /**
      * support for the old getGetParams.  Note this closes the reader.
      * @param reader
      * @return
      * @throws IOException
      * @throws PySyntaxError 
      */
     public static List<Param> getGetParams( Reader reader ) throws IOException, PySyntaxError {
        return getGetParams( readScript(reader) );
     }
     
     public static String readScript( Reader reader ) throws IOException {
        String s;
        StringBuilder build= new StringBuilder();
        BufferedReader breader= new BufferedReader(reader);
        try {
            s= breader.readLine();
            while (s != null) {
               build.append(s).append("\n");
               s = breader.readLine();
            }
        } finally {
            breader.close();
        }
        return build.toString();
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
     * @param script A string containing the entire Jython program.
     * @return list of parameter descriptions, in the order they were encountered in the file.
     * @throws PyException
     */
     public static List<Param> getGetParams( String script ) throws PyException {
         return getGetParams(script,new HashMap<String, String>());
         
     }
     
     /**
      * look through the script, removing expensive calls to make a script
      * that can be executed to get a list of getParam calls.  The user
      * can provide a list of current settings, so that the thread of execution 
      * is matched.
      * @param script any jython script.
      * @param params user-specified values.
      * @return a list of parameters.
      * @throws PyException 
      */
     public static List<Param> getGetParams( String script, Map<String,String>params ) throws PyException {
        
        String prog= simplifyScriptToGetParams(script, true);  // removes calls to slow methods, and gets the essence of the controls of the script.
        
        PythonInterpreter interp;
        try {
            interp= createInterpreter(true);
            setParams( interp, params );
            interp.exec(prog);
        } catch ( IOException ex ) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return new ArrayList();            
        }
        
        interp.exec("import autoplot\n");
        PyList sort= (PyList) interp.eval( "autoplot._paramSort" );

        List<Param> result= new ArrayList();
        for ( int i=0; i<sort.__len__(); i++ ) {
            PyList oo= (PyList) interp.eval( "autoplot._paramMap['"+(String)sort.get(i)+"']" );
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
                } else if ( p.deft instanceof PyJavaInstance ) {
                    Object pp=  ((PyJavaInstance)p.deft).__tojava__( URI.class );
                    if ( pp==Py.NoConversion ) {
                        throw new IllegalArgumentException("unable to use type: "+p.deft);
                    } else {
                        p.type= 'U';
                        p.deft= pp;
                    }
                }
            }
            result.add(p);
        }

        return result;
    }

    /**
     * scrape script for local variables, looking for assignments.
     * @param reader the source for the script.  It is closed when the code executes properly.
     * @return a map of the local variable name to the line containing it.
     */
    public static Map getLocals( BufferedReader reader ) throws IOException {
        
        String s = reader.readLine();

        Pattern assignPattern= Pattern.compile("\\s*([_a-zA-Z][_a-zA-Z0-9]*)\\s*=.*(#(.*))?");
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
     * @param eval string containing the entire program.
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

            while (s != null) {
                
                int comment= s.indexOf("#");
                if ( comment>-1 ) {
                    s= s.substring(0,comment);
                }
                
                boolean sideEffect= true;

                if ( s.length()>1 && Character.isWhitespace( s.charAt(0) ) ) { // just skip over routines.
                    s = reader.readLine();
                    continue;
                }
                
                if ( inDef==false ) {
                    Matcher defm= defPattern.matcher(s);
                    if ( defm.matches() ) {
                        inDef= true;
                        sideEffect= false;
                    }
                } else {
                    Matcher defm= defPattern.matcher(s);
                    if ( defm.matches() ) {
                        if ( sideEffect ) {
                            result.append("  pass\n");
                        }
                    }                    
                    if ( s.length()>0 && !Character.isWhitespace(s.charAt(0)) ) {
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
            if ( inDef ) {
                result.append("  pass\n");
            }
        } catch ( IOException ex ) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } finally {
            try {
                reader.close();
            } catch ( IOException ex ) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        return result.toString();
    }
    
    public static void main( String[] args ) throws IOException {
        main_test1(args);
    }
    
        /**
     * test the getGetParams for a script, seeing if we can reduce 
     * and run the script within interactive time.
     * 
     * @param file
     * @throws Exception 
     */
    private static void doTestGetParams( String testId, String file ) {
        long t0= System.currentTimeMillis();
        System.err.println("== test "+testId+": "+ file + " ==" );
        
        try {
            String script= JythonUtil.readScript( new FileReader(file) );
            String scrip= org.virbo.jythonsupport.JythonUtil.simplifyScriptToGetParams(script,true);
            File f= new File(file);
            String fout= "./test038_"+f.getName();
            FileWriter fw= new FileWriter(fout);
            try {
                fw.append(scrip);
            } finally {
                fw.close();
            }
            List<Param> parms= org.virbo.jythonsupport.JythonUtil.getGetParams( script );
            for ( Param p: parms ) {
                System.err.println(p);
            }
            System.err.println( String.format( "read params in %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.err.println( String.format( "failed within %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
        }

    }
    
    public static void main_test1(String[] args ) throws FileNotFoundException {
        doTestGetParams("006","/home/jbf/ct/hudson/script/test038/yab_20131003.jy"); //TODO: needs fixing
        doTestGetParams("000","/home/jbf/ct/hudson/script/test038/trivial.jy");
        doTestGetParams("001","/home/jbf/ct/hudson/script/test038/demoParms0.jy");
        doTestGetParams("002","/home/jbf/ct/hudson/script/test038/demoParms1.jy");
        doTestGetParams("003","/home/jbf/ct/hudson/script/test038/demoParms.jy");
        doTestGetParams("004","/home/jbf/ct/hudson/script/test038/rbsp/emfisis/background_removal_wfr.jyds");
    }
}
