/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package test.endtoend;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.util.LoggerManager;
import org.python.util.PythonInterpreter;
import org.autoplot.jythonsupport.JythonUtil;
import org.autoplot.jythonsupport.JythonUtil.Param;
import org.autoplot.jythonsupport.SimplifyScriptSupport;
import org.python.core.PyException;

/**
 * Test of Jython features.
 * @author jbf
 */
public class Test038 {
    
    static final Logger logger= Logger.getLogger("autoplot");

    /**
     * test the getGetParams for a script, seeing if we can reduce 
     * and run the script within interactive time.
     * 
     * @param file
     * @throws Exception 
     */
    private static void doTests( String testId, String file ) {
        doTestsGetParams(testId,file);
        doTestsGetCompletions(testId,file);
    }
    
    /**
     * this goes through the entire script, refactoring to make a similar 
     * script which can be used to complete on variable names and function
     * calls.
     * @param testId
     * @param file 
     */
    private static void doTestsGetCompletions( String testId, String file ) {
        long t0= System.currentTimeMillis();
        System.err.println("== test "+testId+": "+ file + " ==" );
        
        try {
            String script= JythonUtil.readScript( new FileReader(file) );
            String scrip= SimplifyScriptSupport.simplifyScriptToCompletions(script);

            File f= new File(file);
            String fout= "./test038_completions_"+f.getName();
            try (FileWriter fw = new FileWriter(fout)) {
                fw.append(scrip);
            }
            List<Param> parms= org.autoplot.jythonsupport.JythonUtil.getGetParams( script );
            for ( Param p: parms ) {
                System.err.println(p);
            }
            System.err.println( String.format( "read params in %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.err.println( String.format( "failed within %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
        }

    }
    
    private static void doTestsGetParams( String testId, String file ) {
        long t0= System.currentTimeMillis();
        System.err.println("== test "+testId+": "+ file + " ==" );
        
        try {
            String script= JythonUtil.readScript( new FileReader(file) );
            String scrip= org.autoplot.jythonsupport.JythonUtil.simplifyScriptToGetParams(script,true);
            File f= new File(file);
            String fout= "./test038_params_"+f.getName();
            try (FileWriter fw = new FileWriter(fout)) {
                fw.append(scrip);
            }
            List<Param> parms= org.autoplot.jythonsupport.JythonUtil.getGetParams( script );
            for ( Param p: parms ) {
                System.err.println(p);
            }
            System.err.println( String.format( "read params in %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
        } catch ( IOException | PyException ex ) {
            ex.printStackTrace();
            System.err.println( String.format( "failed within %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
        }

    }
    
    /**
     * this allows us to make a list of tests with an external file.
     * @param f 
     */
    public static void doTestMany( String f ) {
        System.err.println("Reading tests from file: "+f);
        try ( BufferedReader in= new BufferedReader( new FileReader(f) ) ) {
            String s= in.readLine();
            int i=s.indexOf("#");
            if ( i>0 ) s= s.substring(0,i);
            s= s.trim();
            String[] ss= s.split(" ",-2);
            if ( ss.length==2 ) {
                doTests( ss[0], ss[1] );
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Test038.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Test038.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * Autoplot reduces jython scripts to get not much more than the getParam calls so the
     * script can be executed quickly.
     */
    public static void testGetParams() {
        doTests("009","/home/jbf/ct/hudson/script/test038/jydsCommentBug.jyds");  // Chris has a newline before the closing ).
        doTests("008","/home/jbf/ct/hudson/script/test038/jedi_l3_valid_tofxe_events.jyds");
        doTests("000","/home/jbf/ct/hudson/script/test038/trivial.jy");
        doTests("001","/home/jbf/ct/hudson/script/test038/demoParms0.jy");
        doTests("002","/home/jbf/ct/hudson/script/test038/demoParms1.jy");
        doTests("003","/home/jbf/ct/hudson/script/test038/demoParms.jy");
        doTests("004","/home/jbf/ct/hudson/script/test038/rbsp/emfisis/background_removal_wfr.jyds");
        doTests("005","/home/jbf/ct/hudson/script/test038/demoParms2.jy");
        doTests("006","/home/jbf/ct/hudson/script/test038/fce_A.jyds");
        doTests("007","/home/jbf/ct/hudson/script/test038/fce_A_2.jyds");
        doTestMany("/home/jbf/ct/hudson/script/test038/test038.txt");
    }
    
    public static void main( String[] args ) throws IOException {
    
        long t0= System.currentTimeMillis();
        PythonInterpreter interp= JythonUtil.createInterpreter(true);
        interp.eval("1+2");
        System.err.println( String.format( "== first initialize in %d millis\n", System.currentTimeMillis()-t0 ) );
        
        testGetParams();

    }
}
