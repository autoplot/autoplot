/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.server;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.AutoplotUI;
import org.virbo.autoplot.JythonUtil;
import org.virbo.autoplot.ScriptContext;

/**
 * Handles requests coming in from the server.
 * TODO: check against --script option in Autoplot.  JythonMain had a problem with imports.
 * @author jbf
 */
public class RequestHandler {

    private static final Logger logger= Logger.getLogger("autoplot");

    public RequestHandler() {
    }

    /**
     * this is a hook to remind myself we want to filter the stream to ensure
     * a secure sandbox.  This will be done by removing import statements and
     * exec/eval statements.
     * @param in
     * @return
     */
    private InputStream untaint( InputStream in ) {
        return in;
    }
    /**
     * process the python code in data.  
     * return null or in the future the data to send back.
     * @param in
     * @param model
     * @param out
     * @return 
     */
    public String handleRequest( InputStream in, ApplicationModel model, OutputStream out ) {
        try {
            PythonInterpreter interp = JythonUtil.createInterpreter(true, false);

            interp.execfile(AutoplotUI.class.getResource("appContextImports.py").openStream(), "appContextImports.py");
            interp.setOut( out );
            interp.set("dom", model.getDocumentModel() );
            interp.set("params", new PyDictionary());
            interp.set("resourceURI", Py.None );
            
            ScriptContext._setOutputStream(out); // TODO: this is very kludgy and will surely cause problems
            
            BufferedReader reader= new BufferedReader( new InputStreamReader(in) );
            
            boolean echo= true;
            if ( echo ) out.write("autoplot> ".getBytes());
            String s= reader.readLine();
            while ( s!=null ) {
                try {
                    if ( s.trim().endsWith(";") ) echo= false; else echo=true;
                    interp.exec(s);
                } catch ( RuntimeException ex ) {
                    ex.printStackTrace( new PrintStream( out ) );
                    ex.printStackTrace();
                }
                try {
                    if ( echo ) out.write("autoplot> ".getBytes());
                } catch ( IOException ex ) {
                    // client didn't stick around to get response.
                }
                s = reader.readLine();
            }
                        
            return null;
            
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
            return null;
        }
    }
}
