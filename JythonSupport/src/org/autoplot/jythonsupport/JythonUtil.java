
package org.autoplot.jythonsupport;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.das2.datum.Datum;
import org.das2.datum.DatumRange;
import org.das2.util.LoggerManager;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.python.core.FileUtil;
import org.python.core.Py;
import org.python.core.PyDictionary;
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
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.datasource.DataSetURI;

/**
 * Utilities to support Jython scripting.
 * @see org.autoplot.JythonUtil
 * @author jbf
 */
public class JythonUtil {

    private static final Logger logger= LoggerManager.getLogger("jython");

    /**
     * create an interpreter object configured for Autoplot contexts:
     * <ul>
     *   <li> QDataSets are wrapped so that operators are overloaded.
     *   <li> a standard set of names are imported.
     * </ul>
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
        
        String[] loadClasses= new String[] { "glob.py", "autoplot2017.py", "autoplotapp2017.py" }; // these must be in the root of the interpretter search path.
        for ( String pysrc: loadClasses ) {
            if ( pysrc.equals("glob.py") ) {
                URL jarUrl= InteractiveInterpreter.class.getResource("/"+pysrc);
                if ( jarUrl!=null ) {
                    String f= getLocalJythonLib();
                    pySys.path.insert(0, new PyString( f ));

                } else {
                    logger.log(Level.WARNING, "Couldn''t find jar containing {0}.  See https://sourceforge.net/p/autoplot/bugs/576/", pysrc);
                }
            } else if ( pysrc.equals("autoplotapp2017.py" ) ) {
                String f= getLocalJythonAutoplotAppLib();
                if ( !pySys.path.contains( new PyString(f) ) ) { // TODO possible bug here: PyString/String means local path is in there 4 times.
                    pySys.path.insert(0,new PyString(f) );
                }    
            } else {
                String f= getLocalJythonAutoplotLib();
                if ( !pySys.path.contains( new PyString(f) ) ) {
                    pySys.path.insert(0,new PyString(f) );
                }    
            }
        }

        InteractiveInterpreter interp = new InteractiveInterpreter( null, pySys );
        
//        try {
//            System.err.println("java1-> "+interp.eval("java") );
//        } catch ( Exception ex ) {
//            System.err.println("java1-> ok!" );
//        }
        
        boolean loadAutoplotStuff= true;
        if ( loadAutoplotStuff ) {
            maybeLoadAdapters();
            if ( Util.isLegacyImports() ) {
                URL imports= JythonOps.class.getResource("/imports2017.py");
                if ( imports==null ) {
                    throw new RuntimeException("unable to locate imports2017.py on classpath");
                } else {
                    logger.log(Level.FINE, "loading imports2017.py from {0}", imports);
                }
                InputStream in= imports.openStream(); // note this stream will load in another stream.
                byte[] bimports= FileUtil.readBytes(in);
                String simports= new String( bimports );
                logger.log( Level.FINE, simports );
                //InputStream in = imports.openStream();
                try {
                    interp.execfile( new ByteArrayInputStream(bimports), "/imports2017.py");
                } finally {
                    in.close();
                }
            }
        }

//        try {
//            System.err.println("java2-> "+interp.eval("java") );
//        } catch ( Exception ex ) {
//            System.err.println("java2-> ok!" );
//        }
        
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
        interp.exec("import autoplot2017 as autoplot");
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
        
        try ( InputStream in= JythonOps.class.getResource("/autoplot2017.py").openStream() ) {
            interp.execfile( in, "/autoplot2017.py"); // import everything into default namespace.
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
        int n;
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
        synchronized ( JythonUtil.class ) {
            if ( !ff3.exists() ) {
                if ( !ff3.mkdirs() ) {
                    throw new IOException("Unable to mkdirs "+ff3);
                }
            }
        }
        if ( JythonUtil.class.getResource("/pylisting.txt")==null ) {
            throw new IllegalArgumentException("unable to find pylisting.txt in application, which is needed to install Jython codes.");
        } else {
            logger.log(Level.FINE, "unpacking jython codes in {0}", JythonUtil.class.getResourceAsStream("/pylisting.txt"));
            
            try ( BufferedReader r= new BufferedReader( new InputStreamReader( JythonUtil.class.getResourceAsStream("/pylisting.txt") ) ) ) {
                String s= r.readLine();
                while ( s!=null ) {
                    File ff5= new File( ff3, s );
                    logger.log(Level.FINER, "copy to local folder python code: {0}", s);
                    InputStream in= JythonUtil.class.getResourceAsStream("/"+s);
                    if ( in==null ) {
                        throw new IllegalArgumentException("unable to find jython code which should be embedded in application: "+s);
                    }
                    if ( s.contains("/") ) {
                        if ( !makeHomeFor( ff5 ) ) {
                            throw new IOException("Unable to makeHomeFor "+ff5);
                        }
                    }
                    try (FileOutputStream out = new FileOutputStream( ff5 ) ) {
                        transferStream(in,out);
                    } finally {
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
            }
        }

        logger.fine("   ...done");
        return ff3.toString();
    }
      
    /**
     * copy all the app stuff to autoplot_data/jython without going to web again.  
     * @return the item to add to the python search path.
     * @throws IOException 
     */
    private static String getLocalJythonAutoplotAppLib() throws IOException {
        File ff2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA ) );
        File ff3= new File( ff2.toString() + "/jython" );
        File ff4= new File( ff3.toString(), "pylistingapp2017.txt" );
        if ( ff4.exists() ) {
            return ff3.toString();
        }
        synchronized ( JythonUtil.class ) {
            if ( !ff3.exists() ) {
                if ( !ff3.mkdirs() ) {
                    throw new IOException("Unable to mkdirs "+ff3);
                }
            }
        }
        
        if ( JythonUtil.class.getResource("/pylistingapp2017.txt")==null ) {
            logger.log( Level.FINE, "unable to find pylistingapp2017.txt in application, assuming this is not the Autoplot client application.");
        } else {
            logger.log(Level.FINE, "unpacking jython codes in {0}", JythonUtil.class.getResourceAsStream("/pylistingapp2017.txt"));
            
            try ( BufferedReader r= new BufferedReader( new InputStreamReader( JythonUtil.class.getResourceAsStream("/pylistingapp2017.txt") ) ) ) {
                String s= r.readLine();
                while ( s!=null ) {
                    int i= s.indexOf("#");
                    if ( i>-1 ) s= s.substring(0,i);
                    s= s.trim();
                    if ( s.length()>0 ) {
                        File ff5= new File( ff3, s );
                        logger.log(Level.FINER, "copy to local folder python code: {0}", s);
                        if ( s.contains("/") ) {
                            if ( !makeHomeFor( ff5 ) ) {
                                throw new IOException("Unable to makeHomeFor "+ff5);
                            }
                        }
                        if ( ff5.exists() ) {
                            logger.fine("already have file, skip...");
                            s= r.readLine();
                            continue;
                        }
                        InputStream in= JythonUtil.class.getResourceAsStream("/"+s);
                        if ( in==null ) {
                            throw new IllegalArgumentException("unable to find jython code which should be embedded in application: "+s);
                        }                                        
                        //Re https://sourceforge.net/p/autoplot/bugs/1724/:
                        //Really each file should be copied and then renamed.

                        try (FileOutputStream out = new FileOutputStream( ff5 ) ) {
                            transferStream(in,out);
                        } finally {
                            in.close();
                            if ( new File( ff3, s ).setReadOnly()==false ) {
                                logger.log( Level.FINER, "set read-only on file {0} failed", s );
                            }
                            if ( new File( ff3, s ).setWritable( true, true )==false ) {
                                logger.log( Level.FINER, "set write for user only on file {0} failed", s );
                            }
                        }
                    }
                    s= r.readLine();
                }
            }
        }
        return ff3.toString();
    }
    
    /**
     * copy the two python files specific to Autoplot into the user's autoplot_data/jython folder.
     * This reads the version from the first line of the autoplot2017.py.
     * @return the item to add to the python search path.
     * @throws IOException 
     */
    private static String getLocalJythonAutoplotLib() throws IOException {
        File ff2= new File( AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA ) );
        File ff3= new File( ff2.toString() + "/jython" );
        File ff4= new File( ff3, "autoplot2017.py" );
        String vers= "";
        
        // This is the version that Autoplot would like to find, and should be found within the Java class path.
        double currentVersion= 1.60;  //rfe320 improved getParam support.
                
        if ( ff4.exists() ) {
            try ( BufferedReader r= new BufferedReader( new FileReader( ff4 ) ) ) {
                String line= r.readLine();
                if ( line!=null ) {
                    Pattern versPattern= Pattern.compile("# autoplot2017.py v([\\d\\.]+) .*");  // must be parsable as a double.
                    Matcher m= versPattern.matcher(line);
                    if ( m.matches() ) {
                        vers= m.group(1);
                    }
                }
            }
        }
        
        if ( logger.isLoggable(Level.FINE) ) {
            logger.fine("== JythonUtil getLocalJythonAutoplotLib ==");
            logger.log(Level.FINE, "ff4.exists()={0}", ff4.exists());
            logger.log(Level.FINE, "vers={0}", vers);
            logger.log(Level.FINE, "currentVersion={0}", currentVersion);
        }
        
        if ( ! ff4.exists() || vers.equals("") || Double.parseDouble(vers)<currentVersion ) {
            logger.log(Level.FINE, "looking for version={0} of {1}, but didn''t find it.", new Object[] { currentVersion, ff4 } );
            logger.log(Level.FINE, "doesn't seem like we have the right file, downloading...");
            synchronized ( JythonUtil.class ) {
                if ( !ff3.exists() ) {
                    if ( !ff3.mkdir() ) {
                        throw new IOException("Unable to mkdir "+ff3);
                    }
                }
            }
            String[] ss= new String[] { "autoplot2017.py", "autoplotapp2017.py" };
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
     * @param uri, such as sftp://user@host/script.jy
     * @param errs an empty list where the errors can be logged.
     * @return true if an err is suspected.
     * @throws java.io.IOException
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

    private static boolean haveloadedAdapters= false;
    
    /**
     * load the adapters, once.
     */
    private synchronized static void maybeLoadAdapters() {
        if ( !haveloadedAdapters ) {
            Py.getAdapter().addPostClass(new PyQDataSetAdapter());
            Py.getAdapter().addPostClass(new PyDatumAdapter());
            haveloadedAdapters= true;
        }
    }

    /**
     * TODO: this ought to remove the need for ParametersFormPanel.
     */
    public static class Param {
        public String name;
        public String label; // the label for the variable used in the script
        public Object deft;
        public String doc;
        public List<Object> enums;  // the allowed values
        /**
         * The parameter type:<ul>
         * <li>T (TimeRange), 
         * <li>A (String, but note a string with the values enumerated either T or F is treated as a boolean.)
         * <li>F (Double or Integer), 
         * <li>D (Datum),
         * <li>S (DatumRange),
         * <li>U (Dataset URI),
         * <li>L (URL), 
         * <li>or R (the resource URI) 
         * </ul>
         */
        public char type;
        @Override
        public String toString() {
            return name+"="+deft;
        }
    }

    /**
     * scrape through the script looking for documentation declarations
     * returns an array, possibly containing:<ul>
     *  <li>LABEL few words
     *  <li>TITLE sentence
     *  <li>DESCRIPTION short paragraph
     * </ul>
     * @param reader, open and ready to read, which will be closed.
     * @return the documentation found.
     * @throws java.io.IOException 
     */
     public static Map<String,String> getDocumentation( BufferedReader reader ) throws IOException {

         Map<String,String> result= new HashMap<>();
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
     //TODO: update this after Python upgrade.
     private static final String[] okay= new String[] { "range,", "xrange,", "getParam,", "lower,", "upper,", "URI,", "URL,", "DatumRangeUtil,", "TimeParser",
        "str,", "int,", "long,", "float,", "datum," };
     
     /**
      * return true if the function call is trivial to execute and can be evaluated within a few milliseconds.
      * @param sn
      * @return 
      */
     private static boolean trivialFunctionCall( SimpleNode sn ) {
         if ( sn instanceof Call ) {
             Call c= (Call)sn;
             boolean klugdyOkay= false;
             String ss= c.func.toString();
             for ( String s: okay ) {
                if ( ss.contains(s) ) klugdyOkay= true;
             }
             if ( klugdyOkay==false ) {
                 if ( ss.contains("TimeUtil") && ss.contains("now")  ) {
                     klugdyOkay= true;
                 }
             }
             logger.log(Level.FINER, "trivialFunctionCall={0} for {1}", new Object[]{klugdyOkay, c.func.toString()});
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

     /**
      * put quotes around values that appear to be strings.  We see if it's parsable as a double or the keyword True or False.
      * @param sval the string, for example "2015" "'2015'" or "2015-01-01"
      * @return 2015, "'2015'", "'2015-01-01'"
      */
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
     
     /**
      * pop off the quotes to get the text inside.
      * @param sval "'2015-01-01'"
      * @return  "2015-01-01"
      */
     public static String maybeUnquoteString( String sval ) {
         if ( ( sval.startsWith("'") && sval.endsWith("'") ) 
                 || ( sval.startsWith("\"") && sval.endsWith("\"") ) ) {
             return sval.substring(1,sval.length()-1);
         } else {
             return sval;
         }
     }

    /**
     * put each parameter into the dictionary autoplot.params.
     * @param interp
     * @param paramsl 
     */ 
    public static void setParams( PythonInterpreter interp, Map<String,String> paramsl ) {
        interp.exec("import autoplot2017 as autoplot");
        interp.exec("autoplot.params=dict()");
        for ( Entry<String,String> e : paramsl.entrySet()) {
            String s= e.getKey();
            if ( s.length()==0 ) {
                throw new IllegalArgumentException("param name is \"\": "+s);
            } 
            if ( !s.equals("arg_0") && !s.equals("script") ) {
                try {
                    String sval= e.getValue();
                    sval= maybeQuoteString( sval );
                    logger.log(Level.FINE, "autoplot.params[''{0}'']={1}", new Object[]{s, sval});
                    interp.exec("autoplot.params['" + s + "']=" + sval);
                } catch ( Exception ex ) {
                    logger.warning(ex.getMessage());
                } 
            }
        }
    }

     
     /**
      * return true if we can include this in the script without a huge performance penalty.
      * @param o
      * @return 
      */
     private static boolean simplifyScriptToGetParamsOkay( stmtType o, HashSet<String> variableNames ) {
         //if ( o.beginLine==607 ) {  // leave this commented code as a reference for debugging
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
                 for (exprType target : a.targets) {
                     exprType et = (exprType) target;
                     if (et instanceof Name) {
                         String id = ((Name) target).id;
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
         if ( ( o instanceof org.python.parser.ast.Print ) ) return false;
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
         //if ( line.contains("sTimeBinSeconds") ) {
         //    System.err.println("heresTimeBinSeconds");
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
         int currentLine= 0; // current line we are writing (0 is first line).
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
                 boolean includeBlock;
                 if ( simplifyScriptToGetParamsCanResolve( iff.test, variableNames ) ) {
                     for ( int i=beginLine; i<iff.body[0].beginLine; i++ ) {
                         result.append(ss[i-1]).append("\n");
                     } // write out the 'if' part
                     includeBlock= true;
                 } else {
                     includeBlock= false;
                 }
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
                 if ( includeBlock ) {
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
                 }
                 acceptLine= -1;
             } else {
                 if ( simplifyScriptToGetParamsOkay( o, variableNames ) ) {
                     if ( acceptLine<0 ) {
                         acceptLine= (o).beginLine;
                         for ( int i=currentLine+1; i<acceptLine; i++ ) {
                             result.append("\n");
                             currentLine= acceptLine;
                         }
                     }
                 } else {
                     if ( acceptLine>-1 ) {
                         int thisLine= (o).beginLine;
                         for ( int i=acceptLine; i<thisLine; i++ ) {
                             appendToResult(result,ss[i-1]).append("\n");
                         }
                         appendToResult(result,"\n");
                         currentLine= thisLine;
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
     
     
     private static class VisitNamesVisitorBase<R> extends VisitorBase {
         String name;
         List<SimpleNode> names;
         
         VisitNamesVisitorBase( String name ) {
             this.name= name;
             names= new ArrayList();
         }

        @Override
        public Object visitName(Name node) throws Exception {
            if ( name.equals(node.id) ) {
                names.add(node);
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
             sn.traverse(this);
         }
         /**
          * return the nodes where the name is used.
          * @return 
          */
         public List<SimpleNode> getNames() {
             return names;
         }
     }
     
     /**
      * get the nodes where the symbol is used.
      * @param script the jython script which is parsed.
      * @param symbol the symbol to look for.
      * @return the AST nodes which contain location information.
      */
     public static List<SimpleNode> showUsage( String script, String symbol ) {
         Module n= (Module)org.python.core.parser.parse( script, "exec" );
         VisitNamesVisitorBase vb= new VisitNamesVisitorBase(symbol);
         for ( stmtType st : n.body ) {
            try {
                st.traverse(vb);
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
         }
         return vb.getNames();
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
             String line= ss[i];
             int ich= line.indexOf('#');
             if ( ich>-1 ) line= line.substring(0,ich);
             if ( line.contains("getParam") ) lastLine= i+1;
         }
         
         if ( lastLine==-1 ) {
             return "";
         }
         
         // check for continuation in last getParam call.
         while ( ss.length>lastLine+1 && ss[lastLine].trim().length()>0 && Character.isWhitespace( ss[lastLine].charAt(0) ) ) {
             lastLine++;
         }
         // Chris showed that a closing bracket or paren doesn't need to be indented.  See test038/jydsCommentBug.jyds
         if ( lastLine<ss.length ) {
             String closeParenCheck= ss[lastLine].trim();
             if ( closeParenCheck.equals(")") || closeParenCheck.equals("]") ) {
                 lastLine++;
             }
         }
         
         HashSet variableNames= new HashSet();
         variableNames.add("getParam");  // this is what allows the getParam calls to be included.
         variableNames.add("str");  // include casts.
         variableNames.add("int");
         variableNames.add("long");
         variableNames.add("float");
         variableNames.add("datum");
//         variableNames.add("datumRange");
//         variableNames.add("URI");
//         variableNames.add("URL");
         
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
     
     /**
      * read all the lines of a script into a string
      * @param reader
      * @return
      * @throws IOException 
      */
     public static String readScript( Reader reader ) throws IOException {
        String s;
        StringBuilder build= new StringBuilder();
        try ( BufferedReader breader= new BufferedReader(reader) ) {
            s= breader.readLine();
            while (s != null) {
               build.append(s).append("\n");
               s = breader.readLine();
            }
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
        return getGetParams( null, script, params );
     }
     
     /**
      * look through the script, removing expensive calls to make a script
      * that can be executed to get a list of getParam calls.  The user
      * can provide a list of current settings, so that the thread of execution 
      * is matched.
      * Each parameter is given a type code:
      **<blockquote><pre><small>{@code
      * R resourceURI, special string
      * T timerange, special string
      * A string
      * F float, double, or int
      * U URI
      * D Datum
      * S DatumRange
      * 
      *}</small></pre></blockquote>
      * note "arg_0" "arg_1" are used to refer to positional (unnamed) parameters.
      * 
      * @param env any values which may be defined already, such as "dom" and "monitor"
      * @param script any jython script.
      * @param params user-specified values.
      * @return a list of parameters.
      * @throws PyException 
      */
     public static List<Param> getGetParams( Map<String,Object> env, String script, Map<String,String>params ) throws PyException {        
        String prog= simplifyScriptToGetParams(script, true);  // removes calls to slow methods, and gets the essence of the controls of the script.

        logger.log(Level.FINER, "Simplified script: {0}", prog);
        
        PythonInterpreter interp;
        try {
            interp= createInterpreter(true);         
        } catch ( IOException ex ) {
            return new ArrayList();
        }
        
        if ( env!=null ) {
            for ( Entry<String,Object> ent: env.entrySet() ) {
                if ( ent.getKey()==null ) {
                    logger.log( Level.WARNING, "parameter name was null" );
                } else if ( ent.getValue()==null ) {
                    if ( ent.getKey().equals("dom") ) {
                        logger.log( Level.FINE, "parameter \"dom\" value was set to null" );  // Some scripts don't use dom.
                    } else {
                        logger.log( Level.WARNING, "parameter value was null" );
                    }
                } else {
                    interp.set( ent.getKey(), ent.getValue() );
                }
            }
        }
        
        setParams( interp, params );
        
        try {
            prog= JythonRefactory.fixImports(prog);
        } catch (IOException ex) {
            Logger.getLogger(JythonUtil.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        interp.exec(prog);
        interp.exec("import autoplot2017 as autoplot\n");
        PyList sort= (PyList) interp.eval( "autoplot._paramSort" );
        
        boolean altWhy= false; // I don't know why things are suddenly showing up in this other space.
        if ( sort.isEmpty() ) {
            try {
                sort= (PyList) interp.eval( "_paramSort" );
                if ( sort.size()>0 ) {
                    logger.warning("things are suddenly in the wrong space.  This is because things are incorrectly imported.");
                    altWhy= true;
                }
            } catch ( PyException ex ) {
                // good...
            }
        }
        
        List<Param> result= new ArrayList();
        for ( int i=0; i<sort.__len__(); i++ ) {
            PyList oo= (PyList) interp.eval( "autoplot._paramMap['"+(String)sort.get(i)+"']" );
            if ( altWhy ) {
                 oo= (PyList) interp.eval( "_paramMap['"+(String)sort.get(i)+"']" );
            }
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
            switch (p.name) {
                case "resourceURI":
                    p.type= 'R';
                    p.deft= p.deft.toString();
                    break;
                case "timerange":
                    p.type= 'T';
                    p.deft= p.deft.toString();
                    break;
                default:
                    if ( p.deft instanceof String ) {
                        p.type= 'A';
                        p.deft= p.deft.toString();
                    } else if ( p.deft instanceof PyString ) {
                        p.type= 'A';
                        p.deft= p.deft.toString();
                    } else if ( p.deft instanceof PyInteger ) { //TODO: Consider if int types should be preserved.
                        p.type= 'F';
                        p.deft= ((PyInteger)p.deft).__tojava__(int.class);
                    } else if ( p.deft instanceof PyFloat ) {
                        p.type= 'F';
                        p.deft= ((PyFloat)p.deft).__tojava__(double.class);
                    } else if ( p.deft instanceof PyJavaInstance ) {
                        Object pp=  ((PyJavaInstance)p.deft).__tojava__( URI.class );
                        if ( pp==Py.NoConversion ) {
                            pp=  ((PyJavaInstance)p.deft).__tojava__( Datum.class );
                            if ( pp==Py.NoConversion ) {
                                pp=  ((PyJavaInstance)p.deft).__tojava__( DatumRange.class ); 
                                if ( pp==Py.NoConversion ) {
                                    pp=  ((PyJavaInstance)p.deft).__tojava__( URL.class ); 
                                    p.type= 'L';
                                    p.deft= pp;
                                } else {
                                    p.type= 'S';
                                    p.deft= pp;
                                }
                            } else {
                                p.type= 'D';
                                p.deft= pp;
                            }
                        } else {
                            p.type= 'U';
                            p.deft= pp;
                        }
                    }   break;
            }
            result.add(p);
        }

        return result;
    }

    /**
     * return a list of the getDataSet calls, from index to simplified getDataSet call.
     * Experimental--interface may change
     * @param env
     * @param script
     * @param params
     * @return
     * @throws PyException 
     */ 
    public static Map<String,String> getGetDataSet( Map<String,Object> env, String script, Map<String,String>params ) throws PyException {   
        
        String[] ss= script.split("\n");
        for ( int i=ss.length-1; i>=0; i-- ) {
            if ( !ss[i].contains("getDataSet") ) {
                ss[i]= "";
            } else {
                break;
            }
        }
        
        StringBuilder prog1= new StringBuilder(ss[0]);
        prog1.append("\n");
        for ( int i=1; i<ss.length; i++ ) {
            prog1.append(ss[i]).append("\n");
        }
        String prog= prog1.toString();
        
        logger.log(Level.FINER, "Simplified script: {0}", prog);

        
        PythonInterpreter interp;
        try {
            interp= createInterpreter(true);         
        } catch ( IOException ex ) {
            return Collections.emptyMap();
        }
        
        if ( env!=null ) {
            for ( Entry<String,Object> ent: env.entrySet() ) {
                if ( ent.getKey()==null ) {
                    logger.log( Level.WARNING, "parameter name was null" );
                } else if ( ent.getValue()==null ) {
                    logger.log( Level.WARNING, "parameter value was null" );
                } else {
                    interp.set( ent.getKey(), ent.getValue() );
                }
            }
        }
        
        if ( params!=null ) setParams( interp, params );
        
        interp.set( "timerange", "timerange" );
        
        String redefineGDS= "gds={}\nngds=0\ndef getDataSet( uri, timerange='', map=0 ):\n  global ngds\n  global gdsi\n  gds[ngds]=uri+' '+timerange\n  ngds=ngds+1\n";
        interp.exec( redefineGDS );

        try {
            interp.exec(prog);
        } catch ( PyException ex ) {
            logger.log( Level.WARNING, null, ex );
            throw ex;
        }
        
        Map<String,String> result= new LinkedHashMap<>();
        PyDictionary r= (PyDictionary)interp.get("gds");
        
        for ( Object k : r.keys() ) {
            result.put( k.toString(), r.get(Py.java2py(k)).toString() );
        }
        
        //for ( Entry e : r.entrySet() ) {
        //    result.put( e.getKey().toString(), e.getValue().toString() );
        //}

        return result;
    }
     
    /**
     * scrape script for local variables, looking for assignments.  The reader is closed
     * after reading.
     * @param reader the source for the script.  It is closed when the code executes properly.
     * @return a map of the local variable name to the line containing it.
     * @throws java.io.IOException
     */
    public static Map getLocals( BufferedReader reader ) throws IOException {
        
        try {
            String s = reader.readLine();

            Pattern assignPattern= Pattern.compile("\\s*([_a-zA-Z][_a-zA-Z0-9]*)\\s*=.*(#(.*))?");
            Pattern defPattern= Pattern.compile("def .*");

            boolean inDef= false;

            Map<String,String> result= new LinkedHashMap<>(); // from ID to description

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

            return result;
            
        } finally {
            reader.close();
        }

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
    
//    public static void main( String[] args ) throws IOException {
//        main_test1(args);
//    }
//    
//        /**
//     * test the getGetParams for a script, seeing if we can reduce 
//     * and run the script within interactive time.
//     * 
//     * @param file
//     * @throws Exception 
//     */
//    private static void doTestGetParams( String testId, String file ) {
//        long t0= System.currentTimeMillis();
//        System.err.println("== test "+testId+": "+ file + " ==" );
//        
//        try {
//            String script= JythonUtil.readScript( new FileReader(file) );
//            String scrip= org.autoplot.jythonsupport.JythonUtil.simplifyScriptToGetParams(script,true);
//            File f= new File(file);
//            String fout= "./test038_"+f.getName();
//            try ( FileWriter fw= new FileWriter(fout) ) {
//                fw.append(scrip);
//            }
//            List<Param> parms= org.autoplot.jythonsupport.JythonUtil.getGetParams( script );
//            for ( Param p: parms ) {
//                System.err.println(p);
//            }
//            System.err.println( String.format( "read params in %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
//        } catch ( Exception ex ) {
//            logger.log(Level.WARNING,null,ex);
//            System.err.println( String.format( "failed within %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
//        }
//
//    }
//    
//    public static void main_test1(String[] args ) throws FileNotFoundException {
//        doTestGetParams("006","/home/jbf/ct/hudson/script/test038/yab_20131003.jy"); //TODO: needs fixing
//        doTestGetParams("000","/home/jbf/ct/hudson/script/test038/trivial.jy");
//        doTestGetParams("001","/home/jbf/ct/hudson/script/test038/demoParms0.jy");
//        doTestGetParams("002","/home/jbf/ct/hudson/script/test038/demoParms1.jy");
//        doTestGetParams("003","/home/jbf/ct/hudson/script/test038/demoParms.jy");
//        doTestGetParams("004","/home/jbf/ct/hudson/script/test038/rbsp/emfisis/background_removal_wfr.jyds");
//    }
}
