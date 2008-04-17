/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot.server;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.core.PyStringMap;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.AutoPlotUI;

/**
 *
 * @author jbf
 */
public class RequestHandler {

    public static void doIt(ApplicationModel model) {
        try {

            PythonInterpreter interp = new PythonInterpreter();

            interp.execfile(AutoPlotUI.class.getResource("imports.py").openStream(), "imports.py");


            InputStream in;
            String inIdentifier;

            in = new ByteArrayInputStream("print getApplicationModel().getPlot()\n".getBytes());
            inIdentifier = "";

            interp.execfile(in, inIdentifier);
        } catch (IOException ex) {
            Logger.getLogger(RequestHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void RequestHandler() {
    }

    /**
     * process the python code in data.  
     * return null or in the future the data to send back.
     */
    public String handleRequest(String data, ApplicationModel model, OutputStream out ) {
        try {
            PythonInterpreter interp = new PythonInterpreter();

            interp.execfile(AutoPlotUI.class.getResource("imports.py").openStream(), "imports.py");
            interp.setOut( out );

            InputStream in;
            String inIdentifier;

            in = new ByteArrayInputStream(data.getBytes());
            inIdentifier = "";

            interp.execfile(in, inIdentifier);

            return null;
        } catch (IOException ex) {
            Logger.getLogger(RequestHandler.class.getName()).log(Level.SEVERE, null, ex);
            return null;
        }
    }
}
