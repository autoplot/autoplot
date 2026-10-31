package test;

import org.python.core.Py;
import org.python.core.PyFrame;
import org.python.core.PyObject;
import org.python.core.PyException;
import org.python.core.TraceFunction;
import org.python.util.PythonInterpreter;

public class DebuggerTest {

    public static void main(String[] args) {

        PythonInterpreter interp = new PythonInterpreter();

        TraceFunction trace = new TraceFunction() {

            public TraceFunction traceCall(PyFrame frame) {
                System.out.println("CALL");
                return this;
            }

            public TraceFunction traceLine(PyFrame frame, int line) {
                System.out.println("LINE " + line);
                return this;
            }

            public TraceFunction traceReturn(
                    PyFrame frame, PyObject value) {
                System.out.println("RETURN");
                return this;
            }

            public TraceFunction traceException(
                    PyFrame frame, PyException ex) {
                return this;
            }
        };

        // Install the Java trace function here.
        // We need to verify the 2.2.1 installation API.
        
        Py.getSystemState().settrace( Py.java2py(trace) );
        
        interp.set("debugger", trace);
        
        interp.exec(
                "print 'calcula1e'\n" +
                "print 'calcu2ate'\n" +
                "print 'cal3ulate'\n" +
                "print 'c4lculate'\n" +
                "print 'cal5ulate'\n" );
        
    }
}