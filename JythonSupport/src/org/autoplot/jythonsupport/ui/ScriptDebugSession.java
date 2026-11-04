
package org.autoplot.jythonsupport.ui;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.python.core.PyObject;
import org.python.util.PythonInterpreter;

public class ScriptDebugSession {

    private final PythonInterpreter interp;
    private final JythonDebugger debugger;
    private final ScriptOutputBubbles scriptOutputBubbles;

    public ScriptDebugSession(
            PythonInterpreter interpreter,
            BreakpointModel breakpoints,
            ScriptOutputBubbles scriptOutputBubbles
    ) {

        this.interp = interpreter;
        
        this.scriptOutputBubbles= scriptOutputBubbles;
        
        debugger = new JythonDebugger();
        debugger.setOutputBubbles(scriptOutputBubbles);

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
        if ( debugger.isStopped() ) {
            debugger.evaluate(expr);
            return "(debugger is stopped)";
        }
        if ( !debugger.isPaused() ) {
            return "(debugger not paused)";
        }
        Future<String> f= debugger.evaluate(expr);
        try {
            return f.get();
        } catch (InterruptedException ex) {
            return "not available (interrupted exception)";
        } catch ( ExecutionException ex ) {
            return "not available (execution exception)";
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
