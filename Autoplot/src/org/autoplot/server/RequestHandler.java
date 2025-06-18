
package org.autoplot.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.autoplot.jythonsupport.JythonRefactory;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.util.PythonInterpreter;
import org.autoplot.ApplicationModel;
import org.autoplot.JythonUtil;
import org.autoplot.ScriptContext;
import org.autoplot.scriptconsole.LogConsole;

/**
 * Handles requests coming in from the server.
 * @author jbf
 */
public class RequestHandler {

    private static final Logger logger= org.das2.util.LoggerManager.getLogger("autoplot.server");

    public RequestHandler() {
    }

    /**
     * this is a hook to remind myself we want to filter the stream to ensure
     * a secure sandbox.  This will be done by removing import statements and
     * exec/eval statements, but note the formatDataSet command could be used to
     * write files.
     * 
     * Since connections are only allowed from the localhost, so the restriction
     * on import is being removed.  https://sourceforge.net/p/autoplot/bugs/2448/
     *
     * @param in
     * @return null if the command should not be executed.
     */
    private String untaint( String in, OutputStream out ) {
        return in;
    }
    
    /**
     * process the python code in data.  
     * return null or in the future the data to send back.
     * @param in
     * @param model
     * @param out
     * @param rlistener the 
     * @return 
     */
    public String handleRequest( InputStream in, ApplicationModel model, OutputStream out, RequestListener rlistener) {
        try {
            PythonInterpreter interp = JythonUtil.createInterpreter(true, false, model.getDom(), null );
            interp.setOut( out );
            interp.set("params", new PyDictionary());
            interp.set("resourceURI", Py.None );
            
            model.setPrompt("autoplot> "); // always reset the prompt.
            
            ScriptContext._setOutputStream(out); // TODO: this is very kludgy and will surely cause problems
            
            BufferedReader reader= new BufferedReader( new InputStreamReader(in) );
            
            boolean echo= true;
            if ( echo ) out.write( model.getPrompt().getBytes());
            String s;
            while ( (s=reader.readLine())!=null ) {
                s= untaint(s,out);
                if ( s!=null ) {
                    s= LogConsole.maybeRemovePrompts(s);
                    logger.log(Level.FINE, "executing command: \"{0}\"", s);
                    if ( s.equals("quit") || !rlistener.isListening() ) {
                        rlistener.stopListening();
                        break;
                    }
                    try {
                        echo = !s.trim().endsWith(";");
                        interp.exec(JythonRefactory.fixImports(s));
                    } catch ( RuntimeException ex ) {
                        ex.printStackTrace( new PrintStream( out ) );
                        ex.printStackTrace();
                    }
                }
                try {
                    if ( echo ) out.write( model.getPrompt().getBytes());
                } catch ( IOException ex ) {
                    // client didn't stick around to get response.
                }
            }
                        
            return null;
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            return null;
        }
    }
}
