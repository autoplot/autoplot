package test;

import org.python.core.Py;
import org.python.core.PyDictionary;
import org.python.core.PyObject;
import org.python.core.PyString;
import org.python.util.PythonInterpreter;

public class JythonTraceDemo {

    /**
     * Java callback invoked by the Jython trace function.
     */
    public static class Debugger {

        public void trace(PyObject frame, String event, PyObject arg) {

            if ("line".equals(event)) {
                PyObject code = frame.__getattr__("f_code");
                PyObject filename =
                        code.__getattr__("co_filename");
                PyObject line =
                        frame.__getattr__("f_lineno");

                System.out.println(
                        "LINE " + filename + ":" + line);

                PyObject locals = frame.__getattr__("f_locals");

                System.out.println("    locals: " + locals);

            } else if ("call".equals(event)) {
                PyObject code = frame.__getattr__("f_code");
                PyObject name =
                        code.__getattr__("co_name");

                System.out.println("CALL " + name);

            } else if ("return".equals(event)) {
                System.out.println("RETURN " + arg);
            }
        }
    }

    public static void main(String[] args) {

        PythonInterpreter interp = new PythonInterpreter();
        Debugger debugger = new Debugger();

        // Make the Java debugger available to Jython.
        interp.set("debugger", debugger);

        // Install a Python trace callback that delegates to Java.
        interp.exec(
            "import sys\n" +
            "def debugTrace(frame, event, arg):\n" +
            "    debugger.trace(frame, event, arg)\n" +
            "    return debugTrace\n" +
            "sys.settrace(debugTrace)\n"
        );

        try {
            // Execute a sample script.
            interp.exec(
                "def calculate(x):\n" +
                "    y = x * 2\n" +
                "    z = y + 3\n" +
                "    return z\n" +
                "\n" +
                "a = 5\n" +
                "b = calculate(a)\n" +
                "print 'Result:', b\n"
            );

        } finally {
            // Remove the trace callback.
            interp.exec("sys.settrace(None)");
        }
    }
}
