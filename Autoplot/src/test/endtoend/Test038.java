
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
import javax.swing.JDialog;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import org.autoplot.ScriptContext;
import org.autoplot.dom.Annotation;
import org.autoplot.dom.Application;
import org.python.util.PythonInterpreter;
import org.autoplot.jythonsupport.JythonUtil;
import org.autoplot.jythonsupport.JythonUtil.Param;
import org.autoplot.jythonsupport.SimplifyScriptSupport;
import org.das2.components.DasProgressPanel;
import org.das2.datum.DatumRange;
import org.das2.datum.Units;
import org.das2.jythoncompletion.CompletionContext;
import org.das2.jythoncompletion.CompletionSupport;
import org.das2.jythoncompletion.JythonCompletionTask;
import org.das2.jythoncompletion.ui.CompletionImpl;
import org.das2.jythoncompletion.ui.CompletionResultSetImpl;
import org.das2.qds.QDataSet;
import org.das2.qds.ops.Ops;
import org.das2.qds.util.DataSetBuilder;
import org.das2.util.filesystem.FileSystem;
import org.python.core.PyException;

/**
 * Test of Jython features.
 * @author jbf
 */
public class Test038 {
    
    static final Logger logger= Logger.getLogger("autoplot");
    
    public static final int COMPLETION_ERROR = -99;

    /**
     * test the getGetParams for a script, seeing if we can reduce 
     * and run the script within interactive time.
     * 
     * @param file
     * @throws Exception 
     */
    private static int doTests( String testId, String file ) {
        int t1= doTestsGetParams(testId,file);
        int t2= doTestsGetCompletions(testId,file);
        int t3= doTestsCountCompletions(testId,file);
        return t1!=0 ? t1 : ( t2!=0 ? t2 : t3 );
    }
    
    /**
     * this goes through the entire script, refactoring to make a similar 
     * script which can be used to complete on variable names and function
     * calls.
     * @param testId
     * @param file 
     */
    private static int doTestsGetCompletions( String testId, String file ) {
        long t0= System.currentTimeMillis();
        System.err.println("== test "+testId+": "+ file + " ==" );
        
        try {
            String script= JythonUtil.readScript( new FileReader(file) );
            String scrip= SimplifyScriptSupport.simplifyScriptToCompletions(script);

            File f= new File(file);
            //String fout= "./test038_completions_"+testId+"_"+f.getName();
            String fout= "./test038_completions_"+f.getName();
            try (FileWriter fw = new FileWriter(fout)) {
                fw.append(scrip);
            }
            List<Param> parms= org.autoplot.jythonsupport.JythonUtil.getGetParams( script );
            for ( Param p: parms ) {
                System.err.println(p);
            }
            System.err.println( String.format( "read params in %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
            return 0;
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.err.println( String.format( "failed within %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
            return 1;
        }

    }
    
    public static QDataSet getCompletionsCount( final String ss, int[] positions ) {
        
        final JTextArea tc=new JTextArea();

        tc.setText(ss);
        JDialog d= new JDialog();
        d.getContentPane().add(tc);
        d.pack();
        d.setVisible( true );
        
        final JythonCompletionTask jct=new JythonCompletionTask(tc);
        final CompletionResultSetImpl rs= CompletionImpl.get().createTestResultSet(null,0);
        
        final DasProgressPanel mon=DasProgressPanel.createFramed("checking completions");
        
        DataSetBuilder dsb= new DataSetBuilder(2,100,3);
        for ( int i=0; i<positions.length; i++ ) {
            
            int ip= positions[i];
            int irow,i0,icol;
            if ( mon.isCancelled() ) break;

            tc.setCaretPosition(ip);
            try {
                irow= tc.getLineOfOffset(ip);
                i0= tc.getLineStartOffset(irow);
            } catch (BadLocationException ex) {
                Logger.getLogger(Test038.class.getName()).log(Level.SEVERE, null, ex);
                continue;
            }
            
            icol= ip-i0;
            
            try {
                
                CompletionContext cc= CompletionSupport.getCompletionContext(tc);
                if ( cc!=null ) {
                    int count= jct.doQuery( cc, rs.getResultSet() );
                    dsb.nextRecord( new Object[] { irow+1,icol,count } );
                }
            } catch ( Exception e ) {
                //try {
                //    CompletionContext cc= CompletionSupport.getCompletionContext(tc);
                //    jct.doQuery( cc, rs.getResultSet() );
                //} catch ( Exception e2 ) {
                //    
                //}
                
                e.printStackTrace();
                //CompletionContext cc;
                //try {
                    //cc = CompletionSupport.getCompletionContext(tc);
                    //jct.doQuery( cc, rs.getResultSet() );
                //} catch (BadLocationException ex) {
                //    Logger.getLogger(Test038.class.getName()).log(Level.SEVERE, null, ex);
                //}
                dsb.nextRecord(new Object[] { irow+1,icol, COMPLETION_ERROR} );
            }
        }
        d.setVisible( false );
        d.dispose();
        return dsb.getDataSet();
    }
    
    private static int doTestsCountCompletions(String testId, String file) {
        long t0= System.currentTimeMillis();
        System.err.println("== test "+testId+": "+ file + " ==" );
        
        FileSystem.settings().setOffline(true);
        
        try {
            String script= JythonUtil.readScript( new FileReader(file) );
                        
            File f= new File(file);
            //String fout= "./test038_completions_"+testId+"_"+f.getName();
            
            int n= script.length();
            int[] positions= new int[Math.min(100,n)];
            for ( int i=0; i<positions.length; i++ ) {
                positions[i]= script.length()*i/positions.length;
            }
            QDataSet rr= getCompletionsCount(script,positions);
            QDataSet line= Ops.slice1(rr,0);
            QDataSet column= Ops.slice1(rr,1);
            QDataSet count= Ops.slice1(rr,2);
            ScriptContext.formatDataSet( line, "test038."+f.getName()+".cdf?linenum" );
            ScriptContext.formatDataSet( column, "test038."+f.getName()+".cdf?column&append=T" );
            ScriptContext.formatDataSet( count, "test038."+f.getName()+".cdf?count&append=T" );
            
            ScriptContext.plot(Ops.slice1(rr,0), Ops.slice1(rr,1), Ops.lesserOf(Ops.slice1(rr,2), MAX_COMPLETION_COUNT) );
            QDataSet r= Ops.where( Ops.eq( count, -99 ) );
            
            if ( r.length()>0 ) {
                Application dom= ScriptContext.getDocumentModel();
                int i= (int)r.value(0);
                String s= String.format("Fail @ %d<br>line=%d col=%d", positions[i], (int)line.value(i), (int)column.value(i) );
                dom.getController().addAnnotation( dom.getPlots(0), s );
            }
            
            ScriptContext.waitUntilIdle();
            ScriptContext.setRenderStyle("digital");
            ScriptContext.getDocumentModel().getPlotElements(0).setRenderControl("format=%d&fontSize=0.6em");
            
            ScriptContext.getDocumentModel().getPlots(0).getYaxis().setRange( 
                    DatumRange.newDatumRange( -1, 120, Units.dimensionless ) );
            ScriptContext.getDocumentModel().getPlots(0).getXaxis().setLog(false);
            
            ScriptContext.setTitle( "completions count for "+f.getName() );
            
            String fout= "test038_completions_"+f.getName()+".png";
            ScriptContext.writeToPng( fout );
            return r.length();
            
        } catch ( Exception ex ) {
            ex.printStackTrace();
            System.err.println( String.format( "failed within %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
            return 1;
        }
    }
    public static final int MAX_COMPLETION_COUNT = 77;
    
    private static int doTestsGetParams( String testId, String file ) {
        long t0= System.currentTimeMillis();
        System.err.println("== test "+testId+": "+ file + " ==" );
        
        try {
            String script= JythonUtil.readScript( new FileReader(file) );
            String scrip= org.autoplot.jythonsupport.JythonUtil.simplifyScriptToGetParams(script,true);
            File f= new File(file);
            //String fout= "./test038_params_"+testId+"_"+f.getName();
            String fout= "./test038_params_"+f.getName();
            try (FileWriter fw = new FileWriter(fout)) {
                fw.append(scrip);
            }
            List<Param> parms= org.autoplot.jythonsupport.JythonUtil.getGetParams( script );
            for ( Param p: parms ) {
                System.err.println(p);
            }
            System.err.println( String.format( "read params in %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
            return 0;
        } catch ( IOException | PyException ex ) {
            ex.printStackTrace();
            System.err.println( String.format( "failed within %d millis: %s\n", System.currentTimeMillis()-t0, file ) );
            return 1;
        }

    }
    
    /**
     * this allows us to make a list of tests with an external file.
     * @param f 
     * @return 0 for all success, nonzero otherwise
     */
    public static int doTestMany( String f ) {
        System.err.println("Reading tests from file: "+f);
        int t=0;
        try ( BufferedReader in= new BufferedReader( new FileReader(f) ) ) {
            String s= in.readLine();
            int i=s.indexOf("#");
            if ( i>0 ) s= s.substring(0,i);
            s= s.trim();
            String[] ss= s.split(" ",-2);
            if ( ss.length==2 ) {
                
                t= Math.max( t, doTests( ss[0], ss[1] ) );
                
            }
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Test038.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(Test038.class.getName()).log(Level.SEVERE, null, ex);
        }
        return t;
    }
    
    /**
     * Autoplot reduces jython scripts to get not much more than the getParam calls so the
     * script can be executed quickly.
     */
    public static int testGetParams() {
        int t=0;
        t= Math.max( t, doTests("009","/home/jbf/ct/hudson/script/test038/jydsCommentBug.jyds") );  // Chris has a newline before the closing ).
        t= Math.max( t, doTests("010","/home/jbf/ct/hudson/script/test038/addPointDigitizer.jy") );
        t= Math.max( t, doTests("002","/home/jbf/ct/hudson/script/test038/demoParms1.jy") );
        t= Math.max( t, doTests("008","/home/jbf/ct/hudson/script/test038/jedi_l3_valid_tofxe_events.jyds") );
        t= Math.max( t, doTests("000","/home/jbf/ct/hudson/script/test038/trivial.jy") );
        t= Math.max( t, doTests("001","/home/jbf/ct/hudson/script/test038/demoParms0.jy") );
        t= Math.max( t, doTests("002","/home/jbf/ct/hudson/script/test038/demoParms1.jy") );
        t= Math.max( t, doTests("003","/home/jbf/ct/hudson/script/test038/demoParms.jy") );
        t= Math.max( t, doTests("004","/home/jbf/ct/hudson/script/test038/rbsp/emfisis/background_removal_wfr.jyds") );
        t= Math.max( t, doTests("005","/home/jbf/ct/hudson/script/test038/demoParms2.jy") );
        t= Math.max( t, doTests("006","/home/jbf/ct/hudson/script/test038/fce_A.jyds") );
        t= Math.max( t, doTests("007","/home/jbf/ct/hudson/script/test038/fce_A_2.jyds") );
        t= Math.max( t, doTests("110","/home/jbf/ct/hudson/script/test038/addPointDigitizer.jy") );
        t= Math.max( t, doTests("111","/home/jbf/ct/hudson/script/test038/rewriteF_L0.jy") );
        t= Math.max( t, doTestMany("/home/jbf/ct/hudson/script/test038/test038.txt") );
        return t;
    }
    
    public static void main( String[] args ) throws IOException {
    
        long t0= System.currentTimeMillis();
        PythonInterpreter interp= JythonUtil.createInterpreter(true);
        interp.eval("1+2");
        System.err.println( String.format( "== first initialize in %d millis\n", System.currentTimeMillis()-t0 ) );
        
        if ( testGetParams()==0 ) {
            System.err.println("ALL OKAY!");
        } else {
            throw new IllegalStateException("at least one of the tests failed.");
        }
        System.exit(0);
    }

}
