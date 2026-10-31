package test;
        
import org.python.util.PythonInterpreter;

public class TraceTest {

    public static void main(String[] args) {

        PythonInterpreter interp = new PythonInterpreter();

        interp.exec(
            "import sys\n" +
            "def trace(frame, event, arg):\n" +
            "    if event == 'line':\n" +
            "        print 'TRACE:', frame.f_lineno\n" +
            "    return trace\n" +
            "\n" +
            "def calculate(x):\n" +
            "    y = x * 2\n" +
            "    return y\n" +
            "\n" +
            "sys.settrace(trace)\n" +
            "result = calculate(5)\n" +
            "print 'Result:', result\n" +
            "sys.settrace(None)\n"
        );
    }
}