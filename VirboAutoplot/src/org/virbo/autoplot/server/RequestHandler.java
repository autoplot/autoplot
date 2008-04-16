/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot.server;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.python.core.Py;
import org.python.core.PyObject;
import org.python.core.PyStringMap;
import org.python.util.PythonInterpreter;
import org.virbo.autoplot.ApplicationModel;
import org.virbo.autoplot.JythonMain;

/**
 *
 * @author jbf
 */
public class RequestHandler {
    public void RequestHandler() {
        
    }    
    
    /**
     * process the python code in data.  
     * return null or in the future the data to send back.
     */
    public String handleRequest( String data, ApplicationModel model ) {
        PyObject me= Py.java2py(model);
        PyStringMap map= new PyStringMap();
        map.__setitem__("context", me);
                
        PythonInterpreter interp = new PythonInterpreter(map);


        InputStream in;
        String inIdentifier;

        in = new ByteArrayInputStream(data.getBytes());
        inIdentifier = "";

        interp.execfile(in, inIdentifier);

        return null;    
    }
    
        
}
