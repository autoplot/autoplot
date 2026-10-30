package org.autoplot.scriptconsole;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.python.core.PyInteger;
import org.python.core.PyObject;

public class JythonDebugger {

    private final Set<Integer> breakpoints
            = Collections.synchronizedSet(new HashSet<Integer>());

    private final Object pauseLock = new Object();

    private volatile boolean paused = false;
    private volatile boolean stopped = false;
    private volatile int currentLine = -1;

    public interface DebuggerListener {

        /**
         * Called whenever the interpreter reaches a new source line.
         *
         * @param filename source file being executed
         * @param line line number, starting at 1
         */
        void tracedLine(String filename, int line);

        /**
         * Called when execution pauses at a breakpoint.
         *
         * @param filename source file being executed
         * @param line line number, starting at 1
         */
        void pausedAtLine(String filename, int line);

        /**
         * Called when execution resumes after a breakpoint.
         */
        void resumed();

        /**
         * Called when script execution finishes.
         */
        void finished();
    }
    
    DebuggerListener listener= null;
    
    public void setListener( DebuggerListener listener ) {
        this.listener= listener;
    }

    /**
     * Called by the Jython trace function on each event.
     */
    public void trace(PyObject frame, String event, PyObject arg) {

        if (!"line".equals(event)) {
            return;
        }

        int line = ((PyInteger) frame.__getattr__("f_lineno")).getValue();
        PyObject code = frame.__getattr__("f_code");
        String filename = code.__getattr__("co_filename").toString();

        if (listener != null) {
            listener.tracedLine(filename, line);
        }
    
        if (!breakpoints.contains(line)) {
            return;
        } else {
            if ( listener!=null ) {
                listener.pausedAtLine(filename, line);
            }
        }
        currentLine = line;

        synchronized (pauseLock) {
            if (stopped) {
                return;
            }

            paused = true;

            System.out.println("Paused at line " + line);

            while (paused && !stopped) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    stopped = true;
                    break;
                }
            }
        }
    }

    public void addBreakpoint(int line) {
        breakpoints.add(line);
    }

    public void removeBreakpoint(int line) {
        breakpoints.remove(line);
    }

    public void continueExecution() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notifyAll();
        }
    }

    public void stop() {
        synchronized (pauseLock) {
            stopped = true;
            paused = false;
            pauseLock.notifyAll();
        }
    }

    public boolean isPaused() {
        return paused;
    }

    public int getCurrentLine() {
        return currentLine;
    }
    
    public void finished() {
        if ( listener!=null ) {
            listener.finished();
        }
    }
}
