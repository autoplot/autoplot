/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.das2.jythoncompletion;

import java.io.IOException;
import org.python.util.PythonInterpreter;

/**
 *
 * @author jbf
 */
public interface JythonInterpreterProvider {
    PythonInterpreter createInterpreter() throws IOException ;
}
