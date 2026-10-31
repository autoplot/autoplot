
package org.autoplot.jythonsupport.ui;

import org.python.util.PythonInterpreter;

public class ScriptDebugSession {

    private final PythonInterpreter interp;
    private final JythonDebugger debugger;

    public ScriptDebugSession(
            PythonInterpreter interpreter,
            BreakpointModel breakpoints) {

        this.interp = interpreter;
        debugger = new JythonDebugger();

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

    /**
     * this is a bit different than ChatGPT suggested
     */
    public void finished() {
        debugger.finished();
    }
}
