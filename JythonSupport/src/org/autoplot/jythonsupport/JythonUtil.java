package org.autoplot.jythonsupport;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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
import org.autoplot.datasource.URISplit;
import org.das2.qds.ops.Ops;
import org.python.core.PyTuple;
import org.python.parser.ast.BinOp;
import org.python.parser.ast.TryExcept;

/**
 * Utilities to support Jython scripting.
 *
 * @see org.autoplot.JythonUtil
 * @author jbf
 */
public class JythonUtil {

    private static final Logger logger = LoggerManager.getLogger("jython");

    /**
     * create an interpreter object configured for Autoplot contexts:
     * <ul>
     * <li> QDataSets are wrapped so that operators are overloaded.
     * <li> a standard set of names are imported.
     * </ul>
     * This also adds things to the Jython search path (see
     * getLocalJythonAutoplotLib) so imports will find them.
     *
     * @param sandbox limit symbols to safe symbols for server.
     * @return PythonInterpreter ready for commands.
     * @throws java.io.IOException
     */
    public static InteractiveInterpreter createInterpreter(boolean sandbox) throws IOException {
        if (PySystemState.cachedir == null) {
            String autoplotData= AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA);
            System.setProperty("python.cachedir", autoplotData + "/pycache");
        }
        ///  http://www.gossamer-threads.com/lists/python/python/697524
        org.python.core.PySystemState pySys = new org.python.core.PySystemState();
        //pySys.setdefaultencoding("utf8"); //doesn't work with Jython2.2, try with 2.5

        String[] loadClasses = new String[]{"glob.py", "autoplot2025.py", "autoplotapp2025.py"}; // these must be in the root of the interpretter search path.
        for (String pysrc : loadClasses) {
            switch (pysrc) {
                case "glob.py":
                    URL jarUrl = InteractiveInterpreter.class.getResource("/" + pysrc);
                    if (jarUrl != null) {
                        String f = getLocalJythonLib();
                        pySys.path.insert(0, new PyString(f));
                        
                    } else {
                        logger.log(Level.WARNING, "Couldn''t find jar containing {0}.  See https://sourceforge.net/p/autoplot/bugs/576/", pysrc);
                    }   break;
                case "autoplotapp2017.py":
                    {
                        String f = getLocalJythonAutoplotAppLib();
                        if (!pySys.path.contains(new PyString(f))) { // TODO possible bug here: PyString/String means local path is in there 4 times.
                            pySys.path.insert(0, new PyString(f));
                        }       break;
                    }
                default:
                    {
                        String f = getLocalJythonAutoplotLib();
                        if (!pySys.path.contains(new PyString(f))) {
                            pySys.path.insert(0, new PyString(f));
                        }       break;
                    }
            }
        }

        InteractiveInterpreter interp = new InteractiveInterpreter(null, pySys);

//        try {
//            System.err.println("java1-> "+interp.eval("java") );
//        } catch ( Exception ex ) {
//            System.err.println("java1-> ok!" );
//        }

        //interp.get("peekAt"); // not yet
        
        boolean loadAutoplotStuff = true;
        if (loadAutoplotStuff) {
            maybeLoadAdapters();
            if (Util.isLegacyImports()) {
                URL imports = JythonOps.class.getResource("/imports2025.py");
                if (imports == null) {
                    throw new RuntimeException("unable to locate imports2025.py on classpath");
                } else {
                    logger.log(Level.FINE, "loading imports2025.py from {0}", imports);
                }
                InputStream in = imports.openStream(); // note this stream will load in another stream.
                byte[] bimports = FileUtil.readBytes(in);
                //InputStream in = imports.openStream();
                try {
                    interp.execfile(new ByteArrayInputStream(bimports), "/imports2025.py");
                } finally {
                    in.close();
                }
            }
        }

        interp.set("dataset", new DatasetCommand());
        interp.set("getDataSet", new GetDataSetCommand() );
        interp.set("getDataSets", new GetDataSetsCommand() );
        interp.set("monitor", new NullProgressMonitor());

//        try {
//            System.err.println("java2-> "+interp.eval("java") );
//        } catch ( Exception ex ) {
//            System.err.println("java2-> ok!" );
//        }
        return interp;

    }

    /**
     * set up the interp variables scripts will often use, such as PWD and
     * monitor.
     *
     * @param interp
     * @param pwd
     * @param resourceUri
     * @param paramsl
     * @param mon
     */
    public static void setupInterp(PythonInterpreter interp, String pwd, String resourceUri, Map<String, String> paramsl, ProgressMonitor mon) {
        interp.set("PWD", pwd);
        interp.exec("import autoplot2025 as autoplot");
        interp.exec("autoplot.params=dict()");
        for (Entry<String, String> e : paramsl.entrySet()) {
            String s = e.getKey();
            if (!s.equals("arg_0") && !s.equals("script")) {
                String sval = e.getValue();
                sval = JythonUtil.maybeQuoteString(sval);
                logger.log(Level.FINE, "autoplot.params[''{0}'']={1}", new Object[]{s, sval});
                interp.exec("autoplot.params['" + s + "']=" + sval);
            }
        }

        if (resourceUri != null) {
            interp.set("resourceURI", resourceUri); // legacy
            interp.exec("autoplot.params['" + "resourceURI" + "']=" + JythonUtil.maybeQuoteString(resourceUri));
        }

        interp.set("monitor", mon);

        try (InputStream in = JythonOps.class.getResource("/autoplot2025.py").openStream()) {
            interp.execfile(in, "/autoplot2025.py"); // import everything into default namespace.
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

    }

    /**
     * transfer the contents of in to out. in and out are closed after the
     * operation. //TODO: other implementations of this exist...
     *
     * @param in
     * @param out
     * @throws IOException
     */
    private static void transferStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[2048];
        int n;
        try {
            n = in.read(buf);
            while (n > -1) {
                out.write(buf, 0, n);
                n = in.read(buf);
            }
        } finally {
            out.close();
            in.close();
        }
    }

    /**
     * ensure that the file has a parent writable directory.
     *
     * @param file
     * @return true if the folder could be made.
     */
    private static boolean makeHomeFor(File file) {
        File f = file.getParentFile();
        if (!f.exists()) {
            return f.mkdirs();
        } else {
            return true;
        }
    }

    /**
     * copy everything out to autoplot_data/jython without going to web again.
     * The old code showed issues where autoplot.org could not be resolved.
     *
     * @return the item to add to the Jython search path.
     * @throws IOException
     */
    private static String getLocalJythonLib() throws IOException {
        File ff2 = new File(AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA));
        File ff3 = new File(ff2.toString() + "/jython");
        File ff4 = new File(ff2.toString() + "/jython/zlib.py");
        if (ff4.exists()) {
            return ff3.toString();
        }
        synchronized (JythonUtil.class) {
            if (!ff3.exists()) {
                if (!ff3.mkdirs()) {
                    throw new IOException("Unable to mkdirs " + ff3);
                }
            }
        }
        if (JythonUtil.class.getResource("/pylisting.txt") == null) {
            throw new IllegalArgumentException("unable to find pylisting.txt in application, which is needed to install Jython codes.");
        } else {
            logger.log(Level.FINE, "unpacking jython codes in {0}", JythonUtil.class.getResource("/pylisting.txt"));

            try (BufferedReader r = new BufferedReader(new InputStreamReader(JythonUtil.class.getResourceAsStream("/pylisting.txt")))) {
                String s = r.readLine();
                while (s != null) {
                    File ff5 = new File(ff3, s);
                    logger.log(Level.FINER, "copy to local folder Jython code: {0}", s);
                    InputStream in = JythonUtil.class.getResourceAsStream("/" + s);
                    if (in == null) {
                        throw new IllegalArgumentException("unable to find Jython code which should be embedded in application: " + s);
                    }
                    if (s.contains("/")) {
                        if (!makeHomeFor(ff5)) {
                            throw new IOException("Unable to makeHomeFor " + ff5);
                        }
                    }
                    try (FileOutputStream out = new FileOutputStream(ff5)) {
                        transferStream(in, out);
                    } finally {
                        in.close();
                        if (new File(ff3, s).setReadOnly() == false) {
                            logger.log(Level.FINER, "set read-only on file {0} failed", s);
                        }
                        if (new File(ff3, s).setWritable(true, true) == false) {
                            logger.log(Level.FINER, "set write for user only on file {0} failed", s);
                        }
                    }
                    s = r.readLine();
                }
            }
        }

        logger.fine("   ...done");
        return ff3.toString();
    }

    /**
     * copy all the app stuff to autoplot_data/jython without going to web
     * again.
     *
     * @return the item to add to the Jython search path.
     * @throws IOException
     */
    private static String getLocalJythonAutoplotAppLib() throws IOException {
        File ff2 = new File(AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA));
        File ff3 = new File(ff2.toString() + "/jython");
        File ff4 = new File(ff3.toString(), "pylistingapp2017.txt");
        if (ff4.exists()) {
            return ff3.toString();
        }
        synchronized (JythonUtil.class) {
            if (!ff3.exists()) {
                if (!ff3.mkdirs()) {
                    throw new IOException("Unable to mkdirs " + ff3);
                }
            }
        }

        if (JythonUtil.class.getResource("/pylistingapp2017.txt") == null) {
            logger.log(Level.FINE, "unable to find pylistingapp2017.txt in application, assuming this is not the Autoplot client application.");
        } else {
            logger.log(Level.FINE, "unpacking jython codes in {0}", JythonUtil.class.getResource("/pylistingapp2017.txt"));

            try (BufferedReader r = new BufferedReader(new InputStreamReader(JythonUtil.class.getResourceAsStream("/pylistingapp2017.txt")))) {
                String s = r.readLine();
                while (s != null) {
                    int i = s.indexOf("#");
                    if (i > -1) {
                        s = s.substring(0, i);
                    }
                    s = s.trim();
                    if (s.length() > 0) {
                        File ff5 = new File(ff3, s);
                        logger.log(Level.FINER, "copy to local folder Jython code: {0}", s);
                        if (s.contains("/")) {
                            if (!makeHomeFor(ff5)) {
                                throw new IOException("Unable to makeHomeFor " + ff5);
                            }
                        }
                        if (ff5.exists()) {
                            logger.fine("already have file, skip...");
                            s = r.readLine();
                            continue;
                        }
                        InputStream in = JythonUtil.class.getResourceAsStream("/" + s);
                        if (in == null) {
                            throw new IllegalArgumentException("unable to find jython code which should be embedded in application: " + s);
                        }
                        //Re https://sourceforge.net/p/autoplot/bugs/1724/:
                        //Really each file should be copied and then renamed.

                        try (FileOutputStream out = new FileOutputStream(ff5)) {
                            transferStream(in, out);
                        } finally {
                            in.close();
                            if (new File(ff3, s).setReadOnly() == false) {
                                logger.log(Level.FINER, "set read-only on file {0} failed", s);
                            }
                            if (new File(ff3, s).setWritable(true, true) == false) {
                                logger.log(Level.FINER, "set write for user only on file {0} failed", s);
                            }
                        }
                    }
                    s = r.readLine();
                }
            }
        }
        return ff3.toString();
    }

    /**
     * copy the two Jython files specific to Autoplot into the user's
     * autoplot_data/jython folder. This reads the version from the first line
     * of autoplot2023.py.
     *
     * @return the item to add to the Jython search path.
     * @throws IOException
     */
    private static String getLocalJythonAutoplotLib() throws IOException {
        File ff2 = new File(AutoplotSettings.settings().resolveProperty(AutoplotSettings.PROP_AUTOPLOTDATA));
        File ff3 = new File(ff2.toString() + "/jython");
        File ff4 = new File(ff3, "autoplot2025.py");
        String vers = "";

        // This is the version that Autoplot would like to find, and should be found within the Java class path.
        double currentVersion = 2.00;  //rfe320 improved getParam support.

        if (ff4.exists()) {
            try (BufferedReader r = new BufferedReader(new FileReader(ff4))) {
                String line = r.readLine();
                if (line != null) {
                    Pattern versPattern = Pattern.compile("# autoplot2023.py v([\\d\\.]+) .*");  // must be parsable as a double.
                    Matcher m = versPattern.matcher(line);
                    if (m.matches()) {
                        vers = m.group(1);
                    }
                }
            }
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("== JythonUtil getLocalJythonAutoplotLib ==");
            logger.log(Level.FINE, "ff4.exists()={0}", ff4.exists());
            logger.log(Level.FINE, "vers={0}", vers);
            logger.log(Level.FINE, "currentVersion={0}", currentVersion);
        }

        if (!ff4.exists() || vers.equals("") || Double.parseDouble(vers) < currentVersion) {
            logger.log(Level.FINE, "looking for version={0} of {1}, but didn''t find it.", new Object[]{currentVersion, ff4});
            logger.log(Level.FINE, "doesn't seem like we have the right file, downloading...");
            synchronized (JythonUtil.class) {
                if (!ff3.exists()) {
                    if (!ff3.mkdir()) {
                        throw new IOException("Unable to mkdir " + ff3);
                    }
                }
            }
            String[] ss = new String[]{"autoplot2025.py", "autoplotapp2017.py","autoplotapp2025.py", "autoplotapp2025.py"};
            for (String s : ss) {
                try (InputStream in = JythonUtil.class.getResourceAsStream("/" + s);
                        FileOutputStream out = new FileOutputStream(new File(ff3, s))) {
                    transferStream(in, out);
                }
                if (new File(ff3, s).setReadOnly() == false) {
                    logger.log(Level.FINER, "set read-only on file {0} failed", s);
                }
                if (new File(ff3, s).setWritable(true, true) == false) {
                    logger.log(Level.FINER, "set write for user only on file {0} failed", s);
                }
            }
        }
        logger.fine("   ...done");
        return ff3.toString();
    }

    /**
     * check the script that it doesn't redefine symbol names like "str"
     *
     * @param uri, such as sftp://user@host/script.jy
     * @param errs an empty list where the errors can be logged.
     * @return true if an err is suspected.
     * @throws java.io.IOException
     */
    public static boolean pythonLint(URI uri, List<String> errs) throws IOException {
        LineNumberReader reader;

        File src = DataSetURI.getFile(uri, new NullProgressMonitor());
        reader = new LineNumberReader(new BufferedReader(new FileReader(src)));
        try {
            return pythonLint(reader, errs);
        } finally {
            reader.close();
        }
    }

    /**
     * check the script that it doesn't redefine symbol names like "str"
     *
     * @param reader, which will be not be closed here.
     * @param errs an empty list where the errors can be logged.
     * @return true if an err is suspected.
     * @throws java.io.IOException
     */
    public static boolean pythonLint(LineNumberReader reader, List<String> errs) throws IOException {

        StringBuilder build= new StringBuilder();
        String line;
        while ( ( line= reader.readLine() )!=null ) {
            build.append(line).append("\n");  
        }
        
        String script= build.toString();
        
        List<SimpleNode> ll= StaticCodeAnalysis.showReassignFunctionCall( script, true, null );
        
        for ( int i=0; i<ll.size(); i++ ) {
            SimpleNode n= ll.get(i);
            if ( n instanceof Name ) {
                errs.add( String.format( "%d:%s", n.beginLine, ((Name)n).id ) );
            } else {
                exprType f=  ((Call)n).func ;
                if ( f instanceof Name ) {
                    errs.add( String.format( "%d:%s", n.beginLine, ((Name)f).id) );
                } else {
                    errs.add( String.format( "%d:%s", n.beginLine, f.toString() ) );
                }
            }
        }
        
        
        return errs.size() > 0;

    }

    private static boolean haveloadedAdapters = false;

    /**
     * load the adapters, once.
     */
    private synchronized static void maybeLoadAdapters() {
        if (!haveloadedAdapters) {
            Py.getAdapter().addPostClass(new PyQDataSetAdapter());
            Py.getAdapter().addPostClass(new PyDatumAdapter());
            haveloadedAdapters = true;
        }
    }


    /**
     * scrape through the script looking for documentation declarations returns
     * an map, possibly containing:<ul>
     * <li>LABEL few words
     * <li>TITLE sentence
     * <li>DESCRIPTION short paragraph
     * </ul>
     * This would originally look for lines like:<br>
     * # TITLE: Text Recombinator<br>
     * but this has been deprecated and scripts should use setScriptTitle 
     * and setScriptDescription
     * 
     * @param reader, open and ready to read, which will be closed.
     * @return the documentation found.
     * @see #getDocumentation(java.io.BufferedReader, java.net.URI) 
     * @throws java.io.IOException
     */    
    public static Map<String, String> getDocumentation(BufferedReader reader ) throws IOException {
        return getDocumentation( reader, null );
    }
    
    /**
     * scrape through the script looking for documentation declarations returns
     * an map, possibly containing:<ul>
     * <li>LABEL few words
     * <li>TITLE sentence
     * <li>DESCRIPTION short paragraph
     * </ul>
     * This would originally look for lines like:<br>
     * # TITLE: Text Recombinator<br>
     * but this has been deprecated and scripts should use setScriptTitle 
     * and setScriptDescription
     * 
     * @param reader, open and ready to read, which will be closed.
     * @param resourceURI the location of the script to define PWD.
     * @return the documentation found.
     * @throws java.io.IOException
     */
    public static Map<String, String> getDocumentation(BufferedReader reader, URI resourceURI ) throws IOException {

        String line = reader.readLine();
        StringBuilder scriptBuilder= new StringBuilder();
        while ( line!=null ) {
            scriptBuilder.append(line).append('\n');
            line= reader.readLine();
        }

        String script= scriptBuilder.toString();
        Map<String,Object> env= new HashMap<>();
        
        if ( resourceURI!=null ) {
            URISplit split= URISplit.parse(resourceURI.toString());
            env.put( "PWD", split.path );
        }
                
        ScriptDescriptor sd= org.autoplot.jythonsupport.JythonUtil.describeScript( env, script, null );
        
        Map<String, String> result = new HashMap<>();
        if ( sd.getDescription().length()>0 ) result.put( "DESCRIPTION", sd.getDescription() );
        if ( sd.getTitle().length()>0 ) result.put( "TITLE", sd.getTitle() );
        if ( sd.getLabel().length()>0 ) result.put( "LABEL", sd.getLabel() );
        if ( sd.getIconURL().length()>0 ) result.put( "ICONURL", sd.getIconURL() );
        
        if ( result.isEmpty() ) {
            reader= new BufferedReader( new StringReader(script) );
            String s = reader.readLine();
            Pattern p = Pattern.compile("#\\s*([a-zA-Z]+)\\s*:(.*)");
            while (s != null) {
                Matcher m = p.matcher(s);
                if (m.matches()) {
                    String prop = m.group(1).toUpperCase();
                    String value = m.group(2).trim();
                    if (prop.equals("LABEL") || prop.equals("TITLE") || prop.equals("DESCRIPTION")) {
                        result.put(prop, value);
                    }
                }
                s = reader.readLine();
            }
            reader.close();
        }

        return result;

    }
    
    /**
     * there are a number of functions which take a trivial amount of time to execute and are needed for some scripts, such as the
     * string.upper() function. The commas are to guard against the id being a subset of another id ("lower," does not match
     * "lowercase"). TODO: update this after Python upgrade.
     * @see SimplifyScriptSupport#okay
     */
    final static String[] okay = new String[] {
        "range,", "xrange,", "irange,","map,","join,","len,","dict,","zip,","list,",
        "getParam,", "lower,", "upper,", "URI,", "URL,", "PWD,", "File,",
        "setScriptDescription", "setScriptTitle", "setScriptLabel", "setScriptIcon",
        "DatumRangeUtil,", "TimeParser,",
        "str,", "int,", "long,", "float,", "datum,", "datumRange,","dataset,",
        "indgen,", "findgen,","dindgen,",
        "ones,", "zeros,",
        "linspace,", "logspace,",
        "dblarr,", "fltarr,", "strarr,", "intarr,", "bytarr,",
        "ripples,", "split,", 
        "color,", "colorFromString,", "isinstance,",
        "readConfiguration,"
    };
         
    /**
     * return true if the function call is trivial to execute and can be
     * evaluated within a few milliseconds.
     *
     * @param sn
     * @return
     */
    private static boolean trivialFunctionCall(SimpleNode sn) {
        //there are a number of functions which take a trivial amount of time to execute and are needed for some scripts, such as 
        //the string.upper() function.
        //The commas are to guard against the id being a subset of another id ("lower," does not match "lowercase").
        //TODO: update this after Python upgrade.
        if (sn instanceof Call) {
            Call c = (Call) sn;
            boolean klugdyOkay = false;
            String ss = c.func.toString();
            for (String s : okay) {
                if (ss.contains(s)) {
                    klugdyOkay = true;
                }
            }
            if (ss.startsWith("Attribute[")) {
                klugdyOkay = true; // parms.keys()
            }
            if (klugdyOkay == false) {
                if (ss.contains("TimeUtil") && ss.contains("now")) {
                    klugdyOkay = true;
                }
            }
            logger.log(Level.FINER, "trivialFunctionCall={0} for {1}", new Object[]{klugdyOkay, c.func.toString()});
            return klugdyOkay;
        } else if ( sn instanceof org.python.parser.ast.Num ) {
            return true;
        } else if ( sn instanceof org.python.parser.ast.Name ) {
            return true;
        } else if ( sn instanceof org.python.parser.ast.Str ) {
            return true;
        } else {
            return false;
        }
    }

    private static class MyVisitorBase<R> extends VisitorBase {

        boolean looksOkay = true;
        boolean visitNameFail = false;

        HashSet names = new HashSet();
        SimpleNode node;

        MyVisitorBase(HashSet names, SimpleNode node ) {
            this.names = names;
            this.node = node; // for reference
            if ( this.node.toString().contains("id=r_erg") ) {
                System.err.println("HERE STOP 671");
            }
        }

        @Override
        public Object visitName(Name node) throws Exception {
            if (!names.contains(node.id)) {
                visitNameFail = true;
            }
            return null;
        }

        @Override
        protected Object unhandled_node(SimpleNode sn) throws Exception {
            return sn;
        }

        @Override
        public void traverse(SimpleNode sn) throws Exception {
            if (sn instanceof Call) {
                boolean newLooksOkay = trivialFunctionCall(sn);
                if (!newLooksOkay) {
                    logger.log(Level.FINE, "looksOkay=False, {0}", sn);
                } else {
                    Call c= (Call)sn;
                    for ( exprType e: c.args ) {
                        if ( !simplifyScriptToGetParamsCanResolve( e, names ) ) {
                            newLooksOkay= false;
                            visitNameFail= true;  // TODO: I don't understand why there are two variables...
                        }
                    }
                }
                looksOkay = newLooksOkay;
            } else if (sn instanceof Assign) { // TODO: I have to admit I don't understand what traverse means.  I would have thought it was all nodes...
                Assign a = ((Assign) sn);
                exprType et = a.value;
                if (et instanceof Call) {
                    traverse( et );
                }
            } else if ( sn instanceof BinOp ) {
                traverse(((BinOp) sn).left);
                traverse(((BinOp) sn).right);

            }
        }

        public boolean looksOkay() {
            return looksOkay;
        }

        /**
         * this contains a node whose name we can't resolve.
         *
         * @return
         */
        public boolean visitNameFail() {
            return visitNameFail;
        }
    }

    /**
     * inspect the node to look for function calls that are not to the function
     * "getParam". This is awful code that will be rewritten when we upgrade
     * Python to 2.7.
     *
     * @param o
     * @param variableNames
     * @return
     */
    private static boolean simplifyScriptToGetParamsOkayNoCalls(SimpleNode o, HashSet<String> variableNames) {

        if (o instanceof Call) {
            Call c = (Call) o;

            if (!trivialFunctionCall(c)) {
                logger.finest(String.format("%04d simplify->false: %s", o.beginLine, o.toString()));
                return false;
            }
        }
        MyVisitorBase vb = new MyVisitorBase(variableNames,o);
        try {
            o.traverse(vb);
            logger.finest(String.format(" %04d simplify->%s: %s", o.beginLine, vb.looksOkay(), o));
            return vb.looksOkay();

        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        logger.finest(String.format("!! %04d simplify->false: %s", o.beginLine, o));
        return false;
    }

    /**
     * can we resolve this node given the variable names we know?
     *
     * @param o
     * @param variableNames
     * @return true if the node can be resolved.
     */
    private static boolean simplifyScriptToGetParamsCanResolve(SimpleNode o, HashSet<String> variableNames) {
        //if ( o.beginLine>=617 && o.beginLine<619 ) {
        //    System.err.println( "here at 617-ish");
        //}
        if (o instanceof Name) {
            Name c = (Name) o;
            if (!variableNames.contains(c.id)) {
                logger.finest(String.format("%04d canResolve->false: %s", o.beginLine, o.toString()));
                return false;
            }
        }
        if (o instanceof Attribute) {
            Attribute at = (Attribute) o;
            while (at.value instanceof Attribute || at.value instanceof Subscript) {
                if (at.value instanceof Attribute) {
                    at = (Attribute) at.value;
                } else {
                    Subscript s = (Subscript) at.value;
                    if (s.value instanceof Attribute) {
                        at = (Attribute) s.value;
                    } else {
                        return false; // oh just give up...
                    }
                }
            }
            if (at.value instanceof Name) {
                Name n = (Name) at.value;
                if (!variableNames.contains(n.id)) {
                    return false;
                }
            } else if ( at.value instanceof Call ) {
                return simplifyScriptToGetParamsCanResolve( at.value, variableNames );
            } else {
                return false;
            }
        }
        if ( o instanceof Call ) {  // ds.property( QDataSet.DEPEND_0 )
            Call c= (Call) o;
            if ( c.func instanceof Attribute ) {
                if ( !simplifyScriptToGetParamsCanResolve( c.func, variableNames ) ){
                    return false;
                }
            }
        }
        MyVisitorBase vb = new MyVisitorBase(variableNames,o);
        try {
            o.traverse(vb);
            logger.finest(String.format(" %04d canResolve->%s: %s", o.beginLine, vb.visitNameFail, o));
            return !vb.visitNameFail;

        } catch (Exception ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
        logger.finest(String.format("!! %04d canResolve->false: %s", o.beginLine, o));
        return false;
    }

    /**
     * put quotes around values that appear to be strings. We see if it's
     * parsable as a double or the keyword True or False.
     *
     * @param sval the string, for example "2015" "'2015'" or "2015-01-01"
     * @return 2015, "'2015'", "'2015-01-01'"
     */
    public static String maybeQuoteString(String sval) {
        boolean isNumber = false;
        try {
            Double.parseDouble(sval);
        } catch (NumberFormatException ex) {
            isNumber = false;
        }

        if (!isNumber && !sval.equals("True") && !sval.equals("False")) {
            if (sval.length() == 0 || !(sval.startsWith("'") && sval.endsWith("'"))) {
                sval = String.format("'%s'", sval);
            }
        }
        return sval;

    }

    /**
     * pop off the quotes to get the text inside.
     *
     * @param sval "'2015-01-01'"
     * @return "2015-01-01"
     */
    public static String maybeUnquoteString(String sval) {
        if ((sval.startsWith("'") && sval.endsWith("'"))
                || (sval.startsWith("\"") && sval.endsWith("\""))) {
            return sval.substring(1, sval.length() - 1);
        } else {
            return sval;
        }
    }

    /**
     * put each parameter into the dictionary autoplot.params.
     *
     * @param interp
     * @param paramsl
     */
    public static void setParams(PythonInterpreter interp, Map<String, String> paramsl) {
        interp.exec("import autoplot2025 as autoplot");
        interp.exec("autoplot.params=dict()");
        for (Entry<String, String> e : paramsl.entrySet()) {
            String s = e.getKey();
            if (s.length() == 0) {
                throw new IllegalArgumentException("param name is \"\": " + s);
            }
            if (!s.equals("arg_0") && !s.equals("script")) {
                try {
                    String sval = e.getValue();
                    sval = maybeQuoteString(sval);
                    logger.log(Level.FINE, "autoplot.params[''{0}'']={1}", new Object[]{s, sval});
                    interp.exec("autoplot.params['" + s + "']=" + sval);
                } catch (Exception ex) {
                    logger.warning(ex.getMessage());
                }
            }
        }
    }

    /**
     * return true if the call is a setScriptTitle or setScriptDescription call.
     * setScriptTitle( 'Batch Master Demo' )
     *
     * @param o
     * @param variableNames
     * @return
     */
    private static boolean isSetScriptCall(stmtType o, HashSet<String> variableNames) {
        if (o instanceof org.python.parser.ast.Expr) {
            org.python.parser.ast.Expr expr = (org.python.parser.ast.Expr) o;
            if (expr.value instanceof Call) {
                Call c = ((Call) expr.value);
                if (c.func instanceof Name) {
                    Name n = (Name) c.func;
                    if (n.id.equals("setScriptTitle")
                            || n.id.equals("setScriptDescription")
                            || n.id.equals("setScriptLabel")
                            || n.id.equals("setScriptIcon")) {
                        return true;
                    }
                }
                return false;
            }
            return false;
        }
        return false;
    }

    /**
     * return true if we can include this in the script without a huge
     * performance penalty.
     *
     * @param o
     * @return
     */
    private static boolean simplifyScriptToGetParamsOkay(stmtType o, HashSet<String> variableNames) {
        //if ( o.beginLine==607 ) {  // leave this commented code as a reference for debugging
        //    System.err.println("here at line "+o.beginLine);
        //}
        if ((o instanceof org.python.parser.ast.ImportFrom)) {
            return true;
        }
        if ((o instanceof org.python.parser.ast.Import)) {
            return true;
        }
        if ((o instanceof org.python.parser.ast.Assign)) {
            Assign a = (Assign) o;
            for ( exprType a1 : a.targets ) {
                if ( a1 instanceof Subscript ) {
                    Subscript ss= (Subscript)a1;
                    if ( !simplifyScriptToGetParamsCanResolve(ss.value,variableNames) ) {
                        return false;
                    }
                    if ( !simplifyScriptToGetParamsCanResolve(ss.slice,variableNames) ) {
                        return false;
                    }
                } else if ( a1 instanceof Attribute ) {
                    return false;
                }
            }
            if (simplifyScriptToGetParamsOkayNoCalls(a.value, variableNames)) {
                if (!simplifyScriptToGetParamsCanResolve(a.value, variableNames)) {
                    return false;
                }
                for (exprType target : a.targets) {
                    exprType et = (exprType) target;
                    if (et instanceof Name) {
                        String id = ((Name) target).id;
                        variableNames.add(id);
                        logger.log(Level.FINEST, "assign to variable {0}", id);
                    } else if (et instanceof Attribute) {
                        Attribute at = (Attribute) et;
                        while (at.value instanceof Attribute || at.value instanceof Subscript) {
                            if (at.value instanceof Attribute) {
                                at = (Attribute) at.value;
                            } else {
                                Subscript s = (Subscript) at.value;
                                if (s.value instanceof Attribute) {
                                    at = (Attribute) s.value;
                                } else {
                                    return false; // oh just give up...
                                }
                            }
                        }
                        if (at.value instanceof Name) {
                            Name n = (Name) at.value;
                            if (!variableNames.contains(n.id)) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            } else {
                return false;
            }
        }
        if ((o instanceof org.python.parser.ast.If)) {
            return simplifyScriptToGetParamsOkayNoCalls(o, variableNames);
        }
        if ((o instanceof org.python.parser.ast.Print)) {
            return false;
        }
        if ((o instanceof org.python.parser.ast.Expr)) { // preserve constant strings which are effective documentation
            org.python.parser.ast.Expr exp= (org.python.parser.ast.Expr)o;
            if ( exp.value instanceof org.python.parser.ast.Str ) {
                return true;
            }
        }
        logger.log(Level.FINEST, "not okay to simplify: {0}", o);
        return false;
    }

    private static void maybeAppendSort(String theLine, StringBuilder result) {
        int i = theLine.indexOf("getParam");
        if (i != -1) {
            i = theLine.indexOf("=");
            String v = theLine.substring(0, i).trim();
            int indent = theLine.indexOf(v);
            if (indent > 0) {
                result.append(theLine.substring(0, indent));
            }
            result.append("sort_.append( \'").append(v).append("\')\n");
        }
    }
    
    /**
     * return the indentation string (spaces or tabs or empty) for the line.
     * @param line a line of Jython code.
     * @return the indentation 
     */
    protected static String indentForLine( String line ) {
        String[] ss2 = line.split("\\S", -2);
        String indent = ss2[0];
        return indent;
    }

    /**
     * handle where a line continues on to the next line because of indent,
     * and then where a closing parenthesis might be found as well.
     * @param lines
     * @param iline
     * @return the new line number
     */
    protected static int handleContinue( String[] lines, int iline ) {
        if ( iline==lines.length-1 ) {
            return iline;
        }
        String theLine= lines[iline];
        int i= theLine.indexOf("#");
        if ( i>-1 )  theLine= theLine.substring(0,i);
        if ( theLine.trim().endsWith(":") ) {
            logger.finer("line opens to block");
        }
        String thisIndent= indentForLine(theLine);
        String nextIndent= indentForLine(lines[iline+1]);
        while ( iline<lines.length-2 && nextIndent.startsWith(thisIndent) && nextIndent.length()>thisIndent.length() ) {
            iline++;
            nextIndent= indentForLine(lines[iline+1]);
        }
        if ( iline<lines.length-1 && lines[iline+1].trim().startsWith(")" ) ) {
            iline++;
        }
        return iline;
    
    }
    
    private static StringBuilder appendToResult(StringBuilder result, String line) {
        //if ( line.contains("mlt_full") ) {
        //    System.err.println("here stop");
        //}
        result.append(line);
        return result;
    }
    
    /**
     * one-stop place for splitting a script into lines for simplifyScriptToGetParams
     * This was all motivated by a busy number of lineNumber-1's in the code
     * that would handle the refactorings.
     * @param zerothLine null or the line to use for the first element in the array.
     * @param script the script
     * @return the script with the first line of the script in array[1].
     * @see #simplifyScriptToGetParams(java.lang.String[], org.python.parser.ast.stmtType[], java.util.HashSet, int, int, int) 
     * @see SimplifyScriptSupport#simplifyScriptToGetCompletions(java.lang.String[], org.python.parser.ast.stmtType[], java.util.HashSet, int, int, int) 
     */
    public static String[] splitCodeIntoLines( String zerothLine, String script ) {
        String[] ss1= script.split("\n");
        int headerOffset= zerothLine==null ? 0 : 1;
        int lineCount= ss1.length+headerOffset;
        String[] ss= new String[lineCount];
        if ( headerOffset==1 ) ss[0]= zerothLine;
        System.arraycopy( ss1, 0, ss, headerOffset, ss1.length );
        return ss;
    }

    /**
     * Extracts the parts of the program that get parameters or take a trivial
     * amount of time to execute.  This may call itself recursively when if
     * blocks are encountered. 
     * 
     * This scans through, where acceptLine is the first line we'll accept
     * to the currentLine, copying over script from acceptLine to currentLine.
     * 
     * See test038 (https://jfaden.net/jenkins/job/autoplot-test038/)
     *
     * @param ss the entire script, ss[0] is empty string so that ss[1] is the first line of the script.
     * @param stmts statements being processed.
     * @param variableNames variable/procedure names that have been resolved.
     * @param beginLine first line of the script being processed.
     * @param lastLine INCLUSIVE last line of the script being processed.
     * @param depth recursion depth, for debugging.
     * @return
     * @see SimplifyScriptSupport#simplifyScriptToGetCompletions(java.lang.String[], org.python.parser.ast.stmtType[], java.util.HashSet, int, int, int) 
     */
    public static String simplifyScriptToGetParams(String[] ss, stmtType[] stmts, HashSet variableNames, int beginLine, int lastLine, int depth) {
        String spaces= "                              "
                + "                              "
                + "                              ";
        if ( lastLine>=ss.length ) {
            throw new IllegalArgumentException("lastLine is >= number of lines");
        }
        if ( !ss[0].equals("# simplifyScriptToGetParams") ) {
            throw new IllegalArgumentException("first line must be '# simplifyScriptToGetParams'");
        }
        int acceptLine = -1; // first line to accept
        int currentLine = 1; // current line we are writing (1 is first line).
        StringBuilder result = new StringBuilder();
        for (int istatement = 0; istatement < stmts.length; istatement++) {
            stmtType o = stmts[istatement];
            String theLine= SimplifyScriptSupport.getSourceForStatement( ss, o );
            //if ( stmts.length==109 && o.beginLine >50 ) {
            //    System.err.println("theLine: "+ o.beginLine + " " +theLine);
            //}
            int lineCount= theLine.split("\n",-2).length;
            
            if ( depth==0 ) {
                logger.finest(theLine); //breakpoint here.
                //System.err.println(theLine);
            }
            logger.log( Level.FINER, "line {0}: {1}", new Object[] { o.beginLine, theLine } );
            if ( o.beginLine>0 ) {
                 if ( beginLine<0 && istatement==0 ) acceptLine= o.beginLine;
                 if ( lineCount>1 ) {
                    beginLine= o.beginLine - (lineCount-1) ;
                 } else {
                    beginLine= o.beginLine;
                 }
            } else {
                acceptLine = beginLine; // elif clause in autoplot-test038/lastSuccessfulBuild/artifact/test038_demoParms1.jy
            }
            if (beginLine > lastLine) {
                continue;
            }
            if ( o instanceof TryExcept ) {
                //System.err.println("here try except");
                acceptLine= -1;
                continue;
            }
            
            if (o instanceof org.python.parser.ast.If) {
                if (acceptLine > -1) {
                    for (int i = acceptLine; i < beginLine; i++) {
                        appendToResult(result, ss[i]).append("\n");
                    }
                }
                If iff = (If) o;
                boolean includeBlock;
                if (simplifyScriptToGetParamsCanResolve(iff.test, variableNames)) {
                    for (int i = beginLine; i < iff.body[0].beginLine; i++) {
                        result.append(ss[i]).append("\n");
                    } // write out the 'if' part
                    includeBlock = true;
                } else {
                    includeBlock = false;
                }
                int lastLine1;  //lastLine1 is the last line of the "if" clause.
                int elseLine=-1;
                if (iff.orelse != null && iff.orelse.length > 0) {
                    if (iff.orelse[0].beginLine > 0) {
                        lastLine1 = iff.orelse[0].beginLine - 1;  // -1 is for the "else:" part.
                        if ( ss[lastLine1].trim().startsWith("else") ) {
                            elseLine= lastLine1;
                            lastLine1=lastLine1-1;
                        } else if ( ss[lastLine1].trim().startsWith("elif") ) {
                            elseLine= lastLine1;
                            lastLine1=lastLine1-1;
                        }
                    } else {
                        if (iff.orelse[0] instanceof If) {
                            elseLine = ((If) iff.orelse[0]).test.beginLine;
                            lastLine1= elseLine-1;
                        } else {
                            lastLine1= elseLine+1;
                            logger.fine("failure to deal with another day...");
                            //throw new RuntimeException("this case needs to be dealt with...");
                        }
                    }
                } else if ((istatement + 1) < stmts.length) {
                    lastLine1 = stmts[istatement + 1].beginLine - 1;
                } else {
                    lastLine1 = lastLine;
                }
                if (includeBlock) {
                    String ss1 = simplifyScriptToGetParams(ss, iff.body, variableNames, -1, lastLine1, depth + 1);
                    if (ss1.trim().length() == 0) {
                        String line;
                        if ( iff.body[0].beginLine > 0) {
                            line = ss[iff.body[0].beginLine];
                        } else {
                            line = ss[iff.beginLine];
                        }
                        String indent = indentForLine( line );
                        result.append(indent).append("pass  # ").append(line).append("\n");
                        logger.fine("things have probably gone wrong...");
                    } else {
                        appendToResult(result, ss1);
                    }
                    if (iff.orelse != null) {
                        if ( elseLine>-1 ) {
                            appendToResult(result, ss[elseLine]);
                        } else {
                            //appendToResult(result, ss[iff.orelse[0].beginLine]);
                            logger.fine("failure #2 to deal with another day...");
                        }
                        int lastLine2;
                        if ((istatement + 1) < stmts.length) {
                            lastLine2 = stmts[istatement + 1].beginLine - 1;
                        } else {
                            lastLine2 = lastLine;
                        }
                        String ss2 = simplifyScriptToGetParams(ss, iff.orelse, variableNames, elseLine + 1, lastLine2, depth + 1);
                        if (ss2.length() > 0) {
                            result.append("\n");
                        }
                        appendToResult(result, ss2);
                        if (ss2.length() == 0) { // we didn't add anything...
                            String line;
                            line = ss[iff.orelse[0].beginLine];
                            String indent =  indentForLine( line );
                            result.append("\n").append(indent).append("pass  # ").append(line).append("\n");
                        } else {
                            result.append("\n");  // write of the else or elif line
                        }
                    }
                }
                acceptLine = -1;
            } else {
                if (simplifyScriptToGetParamsOkay(o, variableNames)) {
                    if (acceptLine < 0) {
                        acceptLine = beginLine;
                        for (int i = currentLine; i < acceptLine; i++) {
                            String indent= indentForLine( ss[i] );
                            int icomment= ss[i].indexOf("#");
                            if ( icomment>=0 ) {
                                String line= indent + spaces.substring(indent.length(),icomment)+ss[i].substring(icomment);
                                result.append(line).append("\n");
                            }
                            currentLine = acceptLine;
                        }
                    }
                } else if (isSetScriptCall(o, variableNames)) {
                    if (acceptLine < 0) {
                        acceptLine = beginLine;
                        for (int i = currentLine + 1; i < acceptLine; i++) {
                            result.append("\n");
                            currentLine = acceptLine;
                        }
                    }
                } else {
                    if (acceptLine > -1) {
                        int thisLine = getBeginLine( ss, o ) ;
                        String indent= indentForLine( ss[acceptLine] );
                        for (int i = acceptLine; i < thisLine; i++) {
                            if ( ss[i].contains("getDataSet") ) {
                                appendToResult(result, indent + "pass  #1139  "+ ss[i]).append("\n"); // TODO: kludge--how did this work before???
                            } else {
                                appendToResult(result, ss[i]).append("\n");
                            }
                        }
                        appendToResult(result, "\n");
                        currentLine = o.beginLine;
                        acceptLine = -1;
                    } 
                }
            }
        }
        if (acceptLine > -1) {
            lastLine= handleContinue( ss, lastLine );
            int thisLine = lastLine;
            for (int i = acceptLine; i <= thisLine; i++) {
                appendToResult(result, ss[i]).append("\n");
            }
        }
        return result.toString();
    }

    /**
     * there's a problem where multi-line strings and expressions have a begin line at the end not the beginning.
     * @param ss the script which has been parsed into lines.
     * @param o the AST statement
     * @return the line of the beginning of the statement.
     */
    public static int getBeginLine( String[] ss, stmtType o ) {
        int beginLine= o.beginLine;
        if ( o instanceof org.python.parser.ast.Expr ) {
            org.python.parser.ast.Expr expr= (org.python.parser.ast.Expr)o;
            int bl2= expr.value.beginLine;
            if ( bl2<beginLine ) beginLine= bl2;
        }
        return beginLine;
    }

    /**
     * extracts the parts of the program that get parameters.
     *
     * @param script the entire Jython program
     * @param addSort if true, add parameters to keep track of the order that
     * getParam was called. This has no effect now.
     * @return the Jython program with expensive calls removed, up to the last
     * getParam call.
     * @see SimplifyScriptSupport#simplifyScriptToCompletions(java.lang.String) 
     */
    public static String simplifyScriptToGetParams(String script, boolean addSort) throws PySyntaxError {
        
        Logger llogger= LoggerManager.getLogger("jython.simplify");
        
        String[] ss1 = script.split("\n");
        String[] ss= new String[ss1.length+1];
        System.arraycopy( ss1, 0, ss, 1, ss1.length );
        ss[0]= "# simplifyScriptToGetParams";

        int lastLine = -1; // the last line we need to include
        
        boolean withinSimplifyLine= false;
        
        boolean withinTripleQuote= false;
        for (int ilineNum = 1; ilineNum < ss.length; ilineNum++) {
            String line = ss[ilineNum];
            int ich = line.indexOf('#');
            if (ich > -1) {
                line = line.substring(0, ich);
            }
            if (line.contains("getParam")) {
                llogger.log(Level.FINER, "getParam at line {0}", ilineNum);
                lastLine = ilineNum;
                withinSimplifyLine= true;
            } else if (line.contains("setScriptTitle")) {
                llogger.log(Level.FINER, "setScriptTitle at line {0}", ilineNum);
                lastLine = ilineNum;
                withinSimplifyLine= true;
            } else if (line.contains("setScriptDescription")) {
                llogger.log(Level.FINER, "setScriptDescription at line {0}", ilineNum);
                lastLine = ilineNum;
                withinSimplifyLine= true;
            } else if (line.contains("setScriptLabel")) {
                llogger.log(Level.FINER, "setScriptLabel at line {0}", ilineNum);
                lastLine = ilineNum;
                withinSimplifyLine= true;
            } else if (line.contains("setScriptIcon")) {
                llogger.log(Level.FINER, "setScriptIcon at line {0}", ilineNum);
                lastLine = ilineNum;
                withinSimplifyLine= true;
            } else {
                if ( !withinTripleQuote ) {
                    withinSimplifyLine= false;
                }
            }
            if ( line.contains("'''") ) {
                if ( withinTripleQuote ) {
                    if ( !Character.isWhitespace(line.charAt(0)) && withinSimplifyLine ) {
                        lastLine = ilineNum;
                    }
                }
                if ( withinTripleQuote ) {
                    llogger.log(Level.FINER, "close triple quote at line {0}", ilineNum);
                } else {
                    llogger.log(Level.FINER, "open triple quote at line {0}", ilineNum);
                }
                withinTripleQuote= !withinTripleQuote;
            } 

        }

        if (lastLine == -1) {
            return "";
        }

        // check for continuation in last getParam call.
        while (ss.length > lastLine + 1 && ss[lastLine].trim().length() > 0 && Character.isWhitespace(ss[lastLine].charAt(0))) {
            lastLine++;
        }
        // Chris showed that a closing bracket or paren doesn't need to be indented.  See test038/jydsCommentBug.jyds
        if (lastLine < ss.length) {
            String closeParenCheck = ss[lastLine].trim();
            if (closeParenCheck.equals(")") || closeParenCheck.equals("]")) {
                lastLine++;
            }
        }

        HashSet variableNames = new HashSet();
        variableNames.add("getParam");  // this is what allows the getParam calls to be included.
        variableNames.add("map");
        variableNames.add("str");  // include casts.
        variableNames.add("int");
        variableNames.add("long");
        variableNames.add("float");
        variableNames.add("datum");
        variableNames.add("datumRange");
        variableNames.add("URI");
        variableNames.add("URL");
        variableNames.add("True");
        variableNames.add("False");
        variableNames.add("range");
        variableNames.add("xrange");
        variableNames.add("list");
        variableNames.add("len");
        variableNames.add("map");
        variableNames.add("dict");
        variableNames.add("zip");
        variableNames.add("PWD");
        variableNames.add("dom");

        try {
            Module n = (Module) org.python.core.parser.parse(script, "exec");
            return simplifyScriptToGetParams(ss, n.body, variableNames, 1, lastLine, 0);
        } catch (PySyntaxError ex) {
            throw ex;
        }
    }

    /**
     * support for the old getGetParams. Note this closes the reader.
     *
     * @param reader
     * @return
     * @throws IOException
     * @throws PySyntaxError
     */
    public static List<Param> getGetParams(Reader reader) throws IOException, PySyntaxError {
        return getGetParams(null, readScript(reader), new HashMap<String, String>());
    }

    /**
     * Object containing a description of a script, containing its parameters
     * and title describing it.
     */
    public static interface ScriptDescriptor {

        /**
         * a short label, suitable for a menu item.
         * @return a short label, suitable for a menu item.
         */
        String getLabel();

        /**
         * a one-line title, suitable for a GUI heading.
         * @return a one-line title, suitable for a GUI heading.
         */
        String getTitle();

        /**
         * sentence or paragraph documenting the script.
         * @return 
         */
        String getDescription();

        /**
         * the web address of an icon for the script.
         * @return 
         */
        String getIconURL();

        /**
         * the list of the script parameters, containing the type and default values.
         * @return 
         */
        List<Param> getParams();
        
        //List<Param> getOutputParams();  // this should finally be done as well.
    }

    public static final ScriptDescriptor EMPTY = new ScriptDescriptor() {
        @Override
        public String getLabel() {
            return "";
        }

        @Override
        public String getTitle() {
            return "";
        }

        @Override
        public String getDescription() {
            return "";
        }

        @Override
        public String getIconURL() {
            return "";
        }

        @Override
        public List<Param> getParams() {
            return new ArrayList<>();
        }
    };
    
    public static ScriptDescriptor errorScriptDescriptor( final PySyntaxError ex ) {
        return new ScriptDescriptor() {
            @Override
            public String getLabel() {
                return "ERROR";
            }

            @Override
            public String getTitle() {
                return "PySyntaxError";
            }

            @Override
            public String getDescription() {
                return ex.traceback.dumpStack();
            }

            @Override
            public String getIconURL() {
                return "";
            }

            @Override
            public List<Param> getParams() {
                return Collections.emptyList();
            }
            
        };
    }

    /**
     * return the script description and arguments.
     *
     * @param script the script Jython code.
     * @param params any operator-defined values.
     * @return
     * @throws IOException
     */
    public static ScriptDescriptor describeScript(String script, Map<String, String> params) throws IOException {
        return describeScript( null, script, params );
    }
    
    
   private static PyObject getConstraintP( PyDictionary dict, PyObject key, PyObject deft ) {
       PyObject o= dict.get(key,deft);
       if ( Py.isInstance( o, deft.getType() ) ) {
           return o;
       } else {
           throw new IllegalArgumentException("constaints contains the wrong type");
       }
   }
   
    /**
     * return the script description and arguments.
     * @param env the environment, containing PWD and maybe DOM.
     * @param script the script Jython code.
     * @param params any operator-defined values.
     * @return a description of the script parameters and metadata.
     * @throws IOException
     */
    public static ScriptDescriptor describeScript( Map<String,Object> env, String script, Map<String, String> params) throws IOException {        
        String prog;
        try {
            prog= simplifyScriptToGetParams(script, true);  // removes calls to slow methods, and gets the essence of the controls of the script.
        } catch ( PySyntaxError ex ) {
            return errorScriptDescriptor(ex);
        }

        //org.das2.util.FileUtil.writeStringToFile( new File("/home/jbf/tmp/simplified.jy"), prog );
        
        logger.log(Level.FINER, "Simplified script: {0}", prog);

        PythonInterpreter interp;
        try {
            interp = createInterpreter(true);
        } catch (IOException ex) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
            return EMPTY;
        }

        if (env != null) {
            for (Entry<String, Object> ent : env.entrySet()) {
                if (ent.getKey() == null) {
                    logger.log(Level.WARNING, "parameter name was null");
                } else if (ent.getValue() == null) {
                    if (ent.getKey().equals("dom")) {
                        logger.log(Level.FINE, "parameter \"dom\" value was set to null");  // Some scripts don't use dom.
                    } else {
                        logger.log(Level.WARNING, "parameter value was null");
                    }
                } else {
                    logger.log(Level.FINER, "setting env {0} to {1}", new Object[]{ent.getKey(), ent.getValue()});
                    interp.set(ent.getKey(), ent.getValue());
                }
            }
        }
        
        interp.set("autoplot2025._scriptLabel", "");
        interp.set("autoplot2025._scriptTitle", "");
        interp.set("autoplot2025._scriptDescription", "");
        interp.set("autoplot2025._scriptIcon", "");

        if ( params!=null ) {
            setParams(interp, params);
        }

        try {
            prog = JythonRefactory.fixImports(prog, "<J>");
        } catch (IOException ex) {
            Logger.getLogger(JythonUtil.class.getName()).log(Level.SEVERE, null, ex);
        }

        interp.exec(prog);
        interp.exec("import autoplot2025 as autoplot\n");
        PyList sort = (PyList) interp.eval("autoplot._paramSort");
                
        boolean altWhy = false; // I don't know why things are suddenly showing up in this other space.
        if (sort.isEmpty()) {
            try {
                sort = (PyList) interp.eval("_paramSort");
                if (sort.size() > 0) {
                    logger.warning("things are suddenly in the wrong space.  This is because things are incorrectly imported.");
                    altWhy = true;
                }
            } catch (PyException ex) {
                // good...
            }
        }
        
        LinkedList<String> jsort= new LinkedList<>();
        for (int i = 0; i < sort.__len__(); i++) {
            String item= (String)sort.get(i);
            if ( jsort.contains(item) ) {
                jsort.remove(item);  
            } 
            jsort.add(item); // move it to the end
        }

        final List<Param> result = new ArrayList();
        for (int i = 0; i < jsort.size(); i++) {
            
            String theParamName = (String) jsort.get(i);
            PyList oo = (PyList) interp.eval("autoplot._paramMap['" + theParamName + "']");
            if (altWhy) {
                oo = (PyList) interp.eval("_paramMap['" + theParamName + "']");
            }
            Param p = new Param();
            p.label = theParamName;   // should be name in the script
            if (p.label.startsWith("__")) {
                continue;  // __doc__, __main__ symbols defined by Jython.
            }
            p.name = oo.__getitem__(0).toString(); // name in the URI
            p.deft = oo.__getitem__(1);
            p.doc = oo.__getitem__(2).toString();
            p.constraints= new HashMap<>(); // always have constraints so we don't need null check.
            PyObject oconstraints= oo.__getitem__(3); 
            if (oconstraints instanceof PyList) {
                PyList pyList = ((PyList) oconstraints);
                List<Object> enums = new ArrayList(pyList.size());
                for (int j = 0; j < pyList.size(); j++) {
                    enums.add(j, pyList.get(j));
                }
                p.enums = enums;
            } else if (oconstraints instanceof PyDictionary) {
                PyDictionary pyDict = ((PyDictionary) oconstraints);
                PyObject enumsObject;
                PyObject examplesObject;
                if (pyDict.has_key(new PyString("enum"))) {
                    logger.warning("values should be used instead of enum");
                    enumsObject = pyDict.get(new PyString("enum"));
                } else if (pyDict.has_key(new PyString("values"))) {
                    enumsObject = pyDict.get(new PyString("values"));
                } else {
                    enumsObject = null;
                }
                if (pyDict.has_key(new PyString("examples"))) {
                    examplesObject = pyDict.get(new PyString("examples"));
                } else {
                    examplesObject = null;
                }
                Map<String, Object> constraints = new HashMap<>();
                if ( enumsObject != null ) {
                    if (  enumsObject instanceof PyList ) {
                        PyList enumsList = (PyList) enumsObject;
                        List<Object> enums = new ArrayList(enumsList.size());
                        for (int j = 0; j < enumsList.size(); j++) {
                            enums.add(j, enumsList.get(j));
                        }
                        p.enums = enums; 
                        PyObject labelsObject = pyDict.get(new PyString("labels"));
                        if (labelsObject != null && labelsObject instanceof PyList) {
                            PyList labelsList = (PyList) labelsObject;
                            List<Object> labels = new ArrayList(labelsList.size());
                            for (int j = 0; j < labelsList.size(); j++) {
                                labels.add(j, labelsList.get(j));
                            }
                            constraints.put("labels", labels);
                        }
                        
                    } else {
                        logger.log(Level.WARNING, "should be a list: {0}", enumsObject);
                    }
                }
                if ( examplesObject!=null ) {
                    if (  examplesObject instanceof PyList ) {
                        PyList examplesList = (PyList) examplesObject;
                        List<Object> list = new ArrayList(examplesList.size());
                        for (int j = 0; j < examplesList.size(); j++) {
                            list.add(j, examplesList.get(j));
                        }
                        p.examples = list;
                        PyObject labelsObject = pyDict.get(new PyString("labels"));
                        if (labelsObject != null && labelsObject instanceof PyList) {
                            PyList labelsList = (PyList) labelsObject;
                            List<String> labels = new ArrayList(labelsList.size());
                            for (int j = 0; j < labelsList.size(); j++) {
                                labels.add(j, labelsList.get(j).toString());
                            }
                            constraints.put("labels", labels);
                        }
                        
                    } else {
                        logger.log(Level.WARNING, "should be a list: {0}", enumsObject);
                    }
                }
                
                PyString regex= (PyString)getConstraintP( pyDict, new PyString("regex"), new PyString("") );
                if ( regex.__len__()>0 ) {
                    constraints.put("regex", regex);
                }
                
                PyString stringType= (PyString)getConstraintP( pyDict, new PyString("stringType"), new PyString("") );
                if ( stringType.__len__()>0 ) {
                    constraints.put("stringType", stringType);
                }
                
                                
                p.constraints = constraints;
            }
            p.value = params == null ? null : params.get(p.name);

            if (p.name.equals("resourceUri")) {
                p.name = "resourceURI"; //  I will regret allowing for this sloppiness...
            }
            switch (p.name) {
                case "resourceURI":
                    p.type = 'R';
                    p.deft = p.deft.toString();
                    if (p.value != null) {
                        p.value = p.value.toString();
                    }
                    break;
                case "timerange":
                    p.type = 'T';
                    p.deft = p.deft.toString();
                    if (p.value != null) {
                        p.value = p.value.toString();
                    }
                    break;
                default:
                    if (p.deft instanceof String) {
                        p.type = 'A';
                        p.deft = p.deft.toString();
                        if (p.value != null) {
                            p.value = p.value.toString();
                        }
                    } else if (p.deft instanceof PyString) {
                        p.type = 'A';
                        p.deft = p.deft.toString();
                        if (p.value != null) {
                            p.value = p.value.toString();
                        }
                    } else if (p.deft instanceof PyInteger) { //TODO: Consider if int types should be preserved.
                        p.type = 'F';
                        p.deft = ((PyInteger) p.deft).__tojava__(int.class);
                        if (p.value != null) {
                            if (p.value.equals("False")) {
                                p.value = 0;
                            } else if (p.value.equals("True")) {
                                p.value = 1;
                            } else {
                                p.value = Integer.parseInt(p.value.toString());
                            }
                        }
                    } else if (p.deft instanceof PyFloat) {
                        p.type = 'F';
                        p.deft = ((PyFloat) p.deft).__tojava__(double.class);
                        if (p.value != null) {
                            p.value = Double.parseDouble(p.value.toString());
                        }
                    } else if (p.deft instanceof PyJavaInstance) {
                        Object pp = ((PyJavaInstance) p.deft).__tojava__(URI.class);
                        if (pp == Py.NoConversion) {
                            pp = ((PyJavaInstance) p.deft).__tojava__(Datum.class);
                            if (pp == Py.NoConversion) {
                                pp = ((PyJavaInstance) p.deft).__tojava__(DatumRange.class);
                                if (pp == Py.NoConversion) {
                                    pp = ((PyJavaInstance) p.deft).__tojava__(URL.class);
                                    if (pp == Py.NoConversion) {
                                        pp = ((PyJavaInstance) p.deft).__tojava__(Color.class);
                                        if ( pp == Py.NoConversion ) {
                                            pp = ((PyJavaInstance) p.deft).__tojava__(File.class);
                                            p.type = 'M'; // Local File or Directory
                                            p.deft = pp;                                            
                                        } else {
                                            p.type = 'C';
                                            p.deft = pp;
                                        }
                                    } else {
                                        p.type = 'L';
                                        p.deft = pp;
                                    }
                                } else {
                                    p.type = 'S';
                                    p.deft = pp;
                                }
                            } else {
                                p.type = 'D';
                                p.deft = pp;
                            }
                        } else {
                            p.type = 'U';
                            p.deft = pp;
                        }
                    }
                    break;
            }
            result.add(p);
        }

        final String label = String.valueOf(interp.eval("autoplot._scriptLabel"));
        final String title = String.valueOf(interp.eval("autoplot._scriptTitle"));
        final String description = 
                             String.valueOf(interp.eval("autoplot._scriptDescription"));
        final String icon =  String.valueOf(interp.eval("autoplot._scriptIcon"));

        ScriptDescriptor sd = new ScriptDescriptor() {
            @Override
            public String getLabel() {
                return label;
            }

            @Override
            public String getTitle() {
                return title;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public String getIconURL() {
                return icon;
            }

            @Override
            public List<Param> getParams() {
                return result;
            }

        };

        return sd;

    }

    /**
     * read all the lines of a script into a string. The reader will be closed.
     *
     * @param reader
     * @return
     * @throws IOException
     */
    public static String readScript(Reader reader) throws IOException {
        String s;
        StringBuilder build = new StringBuilder();
        try (BufferedReader breader = new BufferedReader(reader)) {
            s = breader.readLine();
            while (s != null) {
                build.append(s).append("\n");
                s = breader.readLine();
            }
        }
        return build.toString();
    }

    /**
     * <p>
     * scrape through the script looking for getParam calls. These are executed,
     * and we get labels and infer types from the defaults. For example,<br>
     * <tt>getParam( 'foo', 3.0 )</tt> will always return a real and<br>
     * <tt>getParam( 'foo', 3 )</tt> will always return an integer.<br>
     *
     * Other examples include:<br>
     * <tt>getParam( 'foo', 'A', '', [ 'A', 'B' ] )</tt> constrains the values
     * to A or B<br>
     * </p>
     * <p>
     * Thinking about the future, people have asked that human-ready labels be
     * fixed to list selections. Constraints should be added to number
     * parameters to specify ranges. And last it would be nice to specify when a
     * parameter is ignored by the script (dA is not used is mode B is active).
     * <br>
     * <tt>getParam( 'foo', 3, '', { 'min':0, 'max':10 } )</tt> might (Not
     * implemented) constrain ranges<br>
     * <tt>getParam( 'sc', 'A', '', [ 'A', 'B' ], { 'A':'big one', 'B':'little
     * one' } )</tt> might (Not implemented) allow labels<br>
     * <tt>getParam( 'foo', 'dA', '', [], { '_ignoreIf':'sc==B' } )</tt> might
     * (Not implemented) allow groups to be disabled when not active<br>
     * </p>
     * <p>
     * A few things the Autoplot script developer must know:
     * <ul>
     * <li>getParam calls can only contain literals, and each must be executable
     * as if it were the only line of code. This may be relaxed in the future.
     * <li>the entire getParam line must be on one line. This too may be
     * relaxed.
     * </ul>
     * </p>
     *
     * @param script A string containing the entire Jython program.
     * @return list of parameter descriptions, in the order they were
     * encountered in the file.
     * @throws PyException
     */
    public static List<Param> getGetParams(String script) throws PyException {
        return getGetParams(null,script, new HashMap<String, String>());

    }

    /**
     * look through the script, removing expensive calls to make a script that
     * can be executed to get a list of getParam calls. The user can provide a
     * list of current settings, so that the thread of execution is matched.
     *
     * @param script any jython script.
     * @param params user-specified values.
     * @return a list of parameters.
     * @throws PyException
     */
    public static List<Param> getGetParams(String script, Map<String, String> params) throws PyException {
        return getGetParams(null, script, params);
    }

    /**
     * look through the script, removing expensive calls to make a script that
     * can be executed to get a list of getParam calls. The user can provide a
     * list of current settings, so that the thread of execution is matched.
     * Each parameter is given a type code:
     **<blockquote><pre><small>{@code
     * R resourceURI, special string
     * T timerange, special string
     * A string
     * F float, double, or int
     * U URI
     * L URL
     * D Datum
     * S DatumRange
     * C Color
     *
     *}</small></pre></blockquote>
     * note "arg_0" "arg_1" are used to refer to positional (unnamed)
     * parameters.
     *
     * @param env any values which may be defined already, such as "dom" and "PWD"
     * @param script any jython script.
     * @param params user-specified values or null.
     * @return a list of parameters.
     * @throws PyException
     * @deprecated use describeScript
     * @see #describeScript(java.util.Map, java.lang.String, java.util.Map) 
     */
    public static List<Param> getGetParams(Map<String, Object> env, String script, Map<String, String> params) throws PyException {
        String prog = simplifyScriptToGetParams(script, true);  // removes calls to slow methods, and gets the essence of the controls of the script.

        logger.log(Level.FINER, "Simplified script: {0}", prog);

        PythonInterpreter interp;
        try {
            interp = createInterpreter(true);
        } catch (IOException ex) {
            return new ArrayList();
        }

        if (env != null) {
            for (Entry<String, Object> ent : env.entrySet()) {
                if (ent.getKey() == null) {
                    logger.log(Level.WARNING, "parameter name was null");
                } else if (ent.getValue() == null) {
                    if (ent.getKey().equals("dom")) {
                        logger.log(Level.FINE, "parameter \"dom\" value was set to null");  // Some scripts don't use dom.
                    } else {
                        logger.log(Level.WARNING, "parameter value was null");
                    }
                } else {
                    interp.set(ent.getKey(), ent.getValue());
                }
            }
        }

        if (params != null) { // I don't think this has ever worked (jbf,20190923)
            setParams(interp, params);
        }

        try {
            prog = JythonRefactory.fixImports(prog, "<J>");
        } catch (IOException ex) {
            Logger.getLogger(JythonUtil.class.getName()).log(Level.SEVERE, null, ex);
        }

        interp.exec(prog);
        interp.exec("import autoplot2025 as autoplot\n");
        PyList sort = (PyList) interp.eval("autoplot._paramSort");

        boolean altWhy = false; // I don't know why things are suddenly showing up in this other space.
        if (sort.isEmpty()) {
            try {
                sort = (PyList) interp.eval("_paramSort");
                if (sort.size() > 0) {
                    logger.warning("things are suddenly in the wrong space.  This is because things are incorrectly imported.");
                    altWhy = true;
                }
            } catch (PyException ex) {
                // good...
            }
        }

        List<Param> result = new ArrayList();
        for (int i = 0; i < sort.__len__(); i++) {
            String theParamName = (String) sort.get(i);
            PyList oo = (PyList) interp.eval("autoplot._paramMap['" + theParamName + "']");
            if (altWhy) {
                oo = (PyList) interp.eval("_paramMap['" + (String) sort.get(i) + "']");
            }
            Param p = new Param();
            p.label = (String) sort.get(i);   // should be name in the script
            if (p.label.startsWith("__")) {
                continue;  // __doc__, __main__ symbols defined by Jython.
            }
            p.name = oo.__getitem__(0).toString(); // name in the URI
            p.deft = oo.__getitem__(1);
            p.doc = oo.__getitem__(2).toString();
            p.constraints= new HashMap<>();
            if (oo.__getitem__(3) instanceof PyList) {
                PyList pyList = ((PyList) oo.__getitem__(3));
                List<Object> enums = new ArrayList(pyList.size());
                for (int j = 0; j < pyList.size(); j++) {
                    enums.add(j, pyList.get(j));
                }
                p.enums = enums;
            } else if (oo.__getitem__(3) instanceof PyDictionary) {
                PyDictionary pyDict = ((PyDictionary) oo.__getitem__(3));
                PyObject enumsObject;
                if (pyDict.has_key(new PyString("enum"))) {
                    enumsObject = pyDict.pop(new PyString("enum"));
                } else if (pyDict.has_key(new PyString("values"))) {
                    enumsObject = pyDict.pop(new PyString("values"));
                } else {
                    enumsObject = null;
                }
                Map<String, Object> constraints = new HashMap<>();
                if (enumsObject != null && enumsObject instanceof PyList) {
                    PyList enumsList = (PyList) enumsObject;
                    List<Object> enums = new ArrayList(enumsList.size());
                    for (int j = 0; j < enumsList.size(); j++) {
                        enums.add(j, enumsList.get(j));
                    }
                    p.enums = enums;
                    PyObject labelsObject = pyDict.pop( new PyString("labels"), null );
                    if (labelsObject != null && labelsObject instanceof PyList) {
                        PyList labelsList = (PyList) labelsObject;
                        List<Object> labels = new ArrayList(labelsList.size());
                        for (int j = 0; j < labelsList.size(); j++) {
                            labels.add(j, labelsList.get(j));
                        }
                        constraints.put("labels", labels);
                    }
                }
                PyObject pymin= pyDict.get( new PyString("min"), null );
                if ( pymin!=null ) {
                    if ( pymin instanceof PyInteger ) {
                        constraints.put( "min", ((PyInteger)pymin).__tojava__(Integer.class) );
                    } else if ( p.name.equals("timerange") ) {
                        constraints.put( "min", pymin.toString() );
                    } else {
                        constraints.put( "min", Double.parseDouble( pymin.__str__().toString() ) );
                    }
                }
                PyObject pymax= pyDict.get( new PyString("max"), null );
                if ( pymax!=null ) {
                    if ( pymax instanceof PyInteger ) {
                        constraints.put( "max", (pymax).__tojava__(Integer.class) );
                    } else if ( p.name.equals("timerange") ) {
                        constraints.put( "max", pymax.toString() );
                    } else {
                        constraints.put( "max", Double.parseDouble( pymax.__str__().toString() ) );
                    }
                }
                PyString stringType= (PyString)pyDict.get( new PyString("stringType"), null );
                if ( stringType!=null ) {
                    p.constraints.put( "stringType", stringType.toString() );
                }
                PyString regex= (PyString)pyDict.get( new PyString("regex"), null );
                if ( regex!=null ) {
                    p.constraints.put( "regex", regex.toString() );
                }
            }
            p.value = params == null ? null : params.get(p.name);

            if (p.name.equals("resourceUri")) {
                p.name = "resourceURI"; //  I will regret allowing for this sloppiness...
            }
            switch (p.name) {
                case "resourceURI":
                    p.type = 'R';
                    p.deft = p.deft.toString();
                    if (p.value != null) {
                        p.value = p.value.toString();
                    }
                    break;
                case "timerange":
                    p.type = 'T';
                    p.deft = p.deft.toString();
                    if (p.value != null) {
                        p.value = p.value.toString();
                    }
                    break;
                default:
                    if (p.deft instanceof String) {
                        p.type = 'A';
                        p.deft = p.deft.toString();
                        if (p.value != null) {
                            p.value = p.value.toString();
                        }
                    } else if (p.deft instanceof PyString) {
                        p.type = 'A';
                        p.deft = p.deft.toString();
                        if (p.value != null) {
                            p.value = p.value.toString();
                        }
                    } else if (p.deft instanceof PyInteger) { //TODO: Consider if int types should be preserved.
                        p.type = 'F';
                        p.deft = ((PyInteger) p.deft).__tojava__(int.class);
                        if (p.value != null) {
                            if (p.value.equals("False")) {
                                p.value = 0;
                            } else if (p.value.equals("True")) {
                                p.value = 1;
                            } else {
                                p.value = Integer.parseInt(p.value.toString());
                            }
                        }
                    } else if (p.deft instanceof PyFloat) {
                        p.type = 'F';
                        p.deft = ((PyFloat) p.deft).__tojava__(double.class);
                        if (p.value != null) {
                            p.value = Double.parseDouble(p.value.toString());
                        }
                    } else if (p.deft instanceof PyJavaInstance) {
                        Object pp = ((PyJavaInstance) p.deft).__tojava__(URI.class);
                        if (pp == Py.NoConversion) {
                            pp = ((PyJavaInstance) p.deft).__tojava__(Datum.class);
                            if (pp == Py.NoConversion) {
                                pp = ((PyJavaInstance) p.deft).__tojava__(DatumRange.class);
                                if (pp == Py.NoConversion) {
                                    pp = ((PyJavaInstance) p.deft).__tojava__(URL.class);
                                    if (pp == Py.NoConversion) {
                                        pp = ((PyJavaInstance) p.deft).__tojava__(Color.class);
                                        if ( pp == Py.NoConversion ) {
                                            pp = ((PyJavaInstance) p.deft).__tojava__(File.class);
                                            p.type = 'M'; // Local File or Directory
                                            p.deft = pp;                                            
                                        } else {
                                            p.type = 'C';
                                            p.deft = pp;
                                        }
                                    } else {
                                        p.type = 'L';
                                        p.deft = pp;
                                    }
                                } else {
                                    p.type = 'S';
                                    p.deft = pp;
                                }
                            } else {
                                p.type = 'D';
                                p.deft = pp;
                            }
                        } else {
                            p.type = 'U';
                            p.deft = pp;
                        }
                    }
                    break;
            }
            result.add(p);
        }

        return result;
    }

    /**
     * return a list of the getDataSet calls, from index to simplified
     * getDataSet call. Experimental--interface may change
     *
     * @param env
     * @param script
     * @param params
     * @return
     * @throws PyException
     */
    public static Map<String, String> getGetDataSet(Map<String, Object> env, String script, Map<String, String> params) throws PyException {

        String[] ss = script.split("\n");
        for (int i = ss.length - 1; i >= 0; i--) {
            if (!ss[i].contains("getDataSet")) {
                ss[i] = "";
            } else {
                break;
            }
        }

        StringBuilder prog1 = new StringBuilder(ss[0]);
        prog1.append("\n");
        for (int i = 1; i < ss.length; i++) {
            prog1.append(ss[i]).append("\n");
        }
        String prog = prog1.toString();

        logger.log(Level.FINER, "Simplified script: {0}", prog);

        PythonInterpreter interp;
        try {
            interp = createInterpreter(true);
        } catch (IOException ex) {
            return Collections.emptyMap();
        }

        if (env != null) {
            for (Entry<String, Object> ent : env.entrySet()) {
                if (ent.getKey() == null) {
                    logger.log(Level.WARNING, "parameter name was null");
                } else if (ent.getValue() == null) {
                    logger.log(Level.WARNING, "parameter value was null");
                } else {
                    interp.set(ent.getKey(), ent.getValue());
                }
            }
        }

        if (params != null) {
            setParams(interp, params);
        }

        interp.set("timerange", "timerange");

        String redefineGDS = "gds={}\nngds=0\ndef getDataSet( uri, timerange='', map=0 ):\n  global ngds\n  global gdsi\n  gds[ngds]=uri+' '+timerange\n  ngds=ngds+1\n";
        interp.exec(redefineGDS);

        try {
            interp.exec(prog);
        } catch (PyException ex) {
            logger.log(Level.WARNING, null, ex);
            throw ex;
        }

        Map<String, String> result = new LinkedHashMap<>();
        PyDictionary r = (PyDictionary) interp.get("gds");

        for (Object k : r.keys()) {
            result.put(k.toString(), r.get(Py.java2py(k)).toString());
        }

        //for ( Entry e : r.entrySet() ) {
        //    result.put( e.getKey().toString(), e.getValue().toString() );
        //}
        return result;
    }

    /**
     * return a Java Map for a Jython dictionary.
     *
     * @param pd
     * @return
     */
    public static Map pyDictionaryToMap(PyDictionary pd) {
        Map<Object, Object> m = new LinkedHashMap<>();
        for (Object tt : pd.items()) {
            String k = ((PyTuple) tt).get(0).toString();
            Object o = ((PyTuple) tt).get(1);
            if (o instanceof PyString) {
                m.put(k, o.toString());
            } else if (o instanceof PyQDataSet) {
                m.put(k, ((PyQDataSet) o).ds);
            } else if (o instanceof PyDatum) {
                m.put(k, ((PyDatum) o).datum);
            } else if (o instanceof PyFloat) {
                m.put(k, Py.tojava((PyObject) o, "java.lang.Double"));
            } else if (o instanceof PyDictionary) {
                m.put(k, pyDictionaryToMap((PyDictionary) o));
            } else if (o instanceof String) {
                m.put(k, o );
            } else if (o instanceof Integer) {
                m.put(k, o );
            } else if (o instanceof Double) {
                m.put(k, o );
            } else if (o instanceof PyList) {
                m.put(k, o );
            } else {
                logger.log(Level.INFO, "assuming Java type where conversion is not implemented: {0}", o);
                m.put(k, o);
            }
        }
        return m;
    }

    /**
     * scrape script for local variables, looking for assignments. The reader is
     * closed after reading.
     *
     * @param reader the source for the script. It is closed when the code
     * executes properly.
     * @return a map of the local variable name to the line containing it.
     * @throws java.io.IOException
     */
    public static Map getLocals(BufferedReader reader) throws IOException {

        try {
            String s = reader.readLine();

            Pattern assignPattern = Pattern.compile("\\s*([_a-zA-Z][_a-zA-Z0-9]*)\\s*=.*(#(.*))?");
            Pattern defPattern = Pattern.compile("def .*");

            boolean inDef = false;

            Map<String, String> result = new LinkedHashMap<>(); // from ID to description

            while (s != null) {

                if (inDef == false) {
                    Matcher defm = defPattern.matcher(s);
                    if (defm.matches()) {
                        inDef = true;
                    }
                } else {
                    if (s.length() > 0 && !Character.isWhitespace(s.charAt(0))) {
                        Matcher defm = defPattern.matcher(s);
                        inDef = defm.matches();
                    }
                }

                if (!inDef) {
                    Matcher m = assignPattern.matcher(s);
                    if (m.matches()) {
                        if (m.group(3) != null) {
                            result.put(m.group(1), m.group(3));
                        } else {
                            result.put(m.group(1), s);
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
     * return Jython code that is equivalent, except it has no side-effects like
     * plotting. This code is not exact, for example (a,b)= (1,2) is not
     * supported. This code is run to support completions.
     *
     * @param eval string containing the entire program.
     * @return the script as a string, with side-effects removed.
     * @deprecated this should not be used, because newer codes use the
     * fully-implemented Jython parser.
     */
    public static String removeSideEffects(String eval) {
        BufferedReader reader = new BufferedReader(new StringReader(eval));
        StringBuilder result = new StringBuilder();
        try {
            String s = reader.readLine();

            Pattern assignPattern = Pattern.compile("\\s*([_a-zA-Z][_a-zA-Z0-9]*)\\s*=.*(#(.*))?");
            Pattern defPattern = Pattern.compile("def .*");
            Pattern importPattern1 = Pattern.compile("from .*");
            Pattern importPattern2 = Pattern.compile("import .*");

            boolean inDef = false;

            while (s != null) {

                int comment = s.indexOf("#");
                if (comment > -1) {
                    s = s.substring(0, comment);
                }

                boolean sideEffect = true;

                if (s.length() > 1 && Character.isWhitespace(s.charAt(0))) { // just skip over routines.
                    s = reader.readLine();
                    continue;
                }

                if (inDef == false) {
                    Matcher defm = defPattern.matcher(s);
                    if (defm.matches()) {
                        inDef = true;
                        sideEffect = false;
                    }
                } else {
                    Matcher defm = defPattern.matcher(s);
                    if (defm.matches()) {
                        if (sideEffect) {
                            result.append("  pass\n");
                        }
                    }
                    if (s.length() > 0 && !Character.isWhitespace(s.charAt(0))) {
                        inDef = defm.matches();
                        if (inDef) {
                            sideEffect = false; //TODO: what about blank line, this isn't an "END"
                        }
                    }
                    if (inDef && s.trim().equals("pass")) { // syntax error otherwise.
                        sideEffect = false;
                    }
                }

                if (!inDef) {
                    Matcher m = assignPattern.matcher(s);
                    if (m.matches()) {
                        sideEffect = false;
                    } else if (importPattern1.matcher(s).matches()) {
                        sideEffect = false;
                    } else if (importPattern2.matcher(s).matches()) {
                        sideEffect = false;
                    }
                }

                if (!sideEffect) {
                    result.append(s).append("\n");
                }

                s = reader.readLine();
            }
            if (inDef) {
                result.append("  pass\n");
            }
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        } finally {
            try {
                reader.close();
            } catch (IOException ex) {
                logger.log(Level.SEVERE, ex.getMessage(), ex);
            }
        }
        return result.toString();
    }

    /**
     * join the array using the delimiter join( ['a','b'], '_' ) -> a_b Note
     * Java 8 finally has a join, and this should be used when Java 8 is
     * available.
     *
     * @param list strings to join
     * @param delim
     * @return the joined string
     */
    public static String join(String[] list, String delim) {
        return join(Arrays.asList(list), delim);
    }

    /**
     * join the array using the delimiter join( ['a','b'], '_' ) -> a_b Note
     * Java 8 finally has a join, and this should be used when Java 8 is
     * available.
     *
     * @param list strings to join
     * @param delim
     * @return the joined string
     */
    public static String join(List<String> list, String delim) {
        if (list.isEmpty()) {
            return "";
        } else {
            StringBuilder result = new StringBuilder(list.get(0));
            for (int i = 1; i < list.size(); i++) {
                result.append(delim).append(list.get(i));
            }
            return result.toString();
        }

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
