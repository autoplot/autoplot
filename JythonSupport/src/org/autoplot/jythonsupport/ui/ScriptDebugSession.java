
package org.autoplot.jythonsupport.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.python.util.PythonInterpreter;

public class ScriptDebugSession {

    private final PythonInterpreter interp;
    private final JythonDebugger debugger;

    public ScriptDebugSession(
            PythonInterpreter interpreter,
            BreakpointModel breakpoints) {

        this.interp = interpreter;
        debugger = new JythonDebugger();

        breakpoints.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                debugger.setBreakpoints(breakpoints.getBreakpoints());
            }
        });
        debugger.setBreakpoints(breakpoints.getBreakpoints());
        
        interp.set("debugger",debugger);

        //debugger.addBreakpoint(3);

        interp.exec(
            "import sys\n" +
            "def debugTrace(frame, event, arg):\n" +
            "    debugger.trace(frame, event, arg)\n" +
            "    return debugTrace\n" +
            "sys.settrace(debugTrace)\n"
        );
    }

    public void continueExecution() {
        debugger.continueExecution();
    }

    public void stepInto() {
        debugger.stepInto();
    }

    public void stepOver() {
        debugger.stepOver();
    }

    public void stop() {
        debugger.stop();
    }
    
    public void setSessionListener(JythonDebugger.Listener listener) {
        debugger.setListener(listener);
    }
    
    public String eval( String expr ) {
        Future<String> f= debugger.evaluate(expr);
        try {
            return f.get();
        } catch (InterruptedException | ExecutionException ex) {
            return "not available";
        }
    }

    public JythonDebugger getDebugger() {
        return this.debugger;
    }
    
    /**
     * this is a bit different than ChatGPT suggested
     */
    public void finished() {
        debugger.finished();
    }
}
