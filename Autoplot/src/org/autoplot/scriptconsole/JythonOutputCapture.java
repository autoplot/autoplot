package org.autoplot.scriptconsole;

import java.io.OutputStream;
import java.io.PrintStream;
import org.python.core.Py;
import org.python.core.PyFrame;
import org.python.util.PythonInterpreter;

/**
 * Intercepts Python-level stdout while retaining the normal console output.
 */
public final class JythonOutputCapture {

    public interface Sink {

        void output(String filename, int line, String text);
    }

    public static final class Writer extends OutputStream {

        private final PrintStream original;
        private final Sink sink;

        public Writer(PrintStream original, Sink sink) {
            this.original = original;
            this.sink = sink;
        }

        @Override
        public void write(int b) {
            byte[] one = {(byte) b};
            write(one, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            original.write(b, off, len);
            // Source location must be captured synchronously, on this thread.
            PyFrame frame = Py.getFrame();
            if (frame == null || len == 0) {
                return;
            }
            String filename = frame.f_code.co_filename;
            int line = frame.f_lineno;
            sink.output(filename, line,
                    new String(b, off, len, java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public void flush() {
            original.flush();
        }
    }

    public static void install(PythonInterpreter interpreter,
            PrintStream console, Sink sink) {
        // Jython's interpreter.setOut(OutputStream) adapts a Java stream
        // into Python sys.stdout; the script's print statements use it.
        interpreter.setOut(new Writer(console, sink));
    }
}
