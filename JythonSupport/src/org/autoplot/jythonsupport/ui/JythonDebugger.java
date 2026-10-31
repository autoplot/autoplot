package org.autoplot.jythonsupport.ui;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.python.core.PyFrame;
import org.python.core.PyObject;

/**
 * Line-oriented debugger for Jython scripts.
 *
 * Install a Python trace function which delegates to trace(), and returns itself for every event. Run the script on a worker
 * thread, never the EDT.
 *
 * Breakpoints are 1-based line numbers in the traced script. The caller should filter trace events by filename if imported modules
 * are traced.
 */
public final class JythonDebugger {

    public interface Listener {
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

    private enum Mode {
        RUNNING, PAUSED, STEP_INTO, STEP_OVER, STEP_OUT, STOPPED
    }

    private final Object pauseLock = new Object();
    private volatile Set<Integer> breakpoints = Collections.emptySet();
    private volatile Listener listener;

    // Accessed only while holding pauseLock.
    private Mode mode = Mode.RUNNING;
    private PyFrame currentFrame;
    private PyFrame stepFrame;
    private PyFrame targetFrame;
    private boolean pauseOnNextLine;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setBreakpoints(Set<Integer> lines) {
        if (lines == null) {
            breakpoints = Collections.emptySet();
        } else {
            breakpoints = Collections.unmodifiableSet(new HashSet<Integer>(lines));
        }
    }

    public Set<Integer> getBreakpoints() {
        return breakpoints;
    }

    /**
     * Continue until the next breakpoint or completion.
     */
    public void continueExecution() {
        resume(Mode.RUNNING);
    }

    /**
     * Stop at the next line event, including inside a called function.
     */
    public void stepInto() {
        resume(Mode.STEP_INTO);
    }

    /**
     * Execute the current statement, stopping in the same frame or caller.
     */
    public void stepOver() {
        synchronized (pauseLock) {
            if (mode != Mode.PAUSED) {
                return;
            }
            stepFrame = currentFrame;
            targetFrame = null;
            pauseOnNextLine = false;
            mode = Mode.STEP_OVER;
            pauseLock.notifyAll();
        }
    }

    /**
     * Continue until execution returns to the caller of the current frame.
     */
    public void stepOut() {
        synchronized (pauseLock) {
            if (mode != Mode.PAUSED) {
                return;
            }
            stepFrame = currentFrame;
            targetFrame = null;
            pauseOnNextLine = false;
            mode = Mode.STEP_OUT;
            pauseLock.notifyAll();
        }
    }

    /**
     * Request that the script stop at its next trace event.
     */
    public void stop() {
        synchronized (pauseLock) {
            mode = Mode.STOPPED;
            pauseLock.notifyAll();
        }
    }

    public boolean isPaused() {
        synchronized (pauseLock) {
            return mode == Mode.PAUSED;
        }
    }

    public boolean isStopped() {
        synchronized (pauseLock) {
            return mode == Mode.STOPPED;
        }
    }

    private void resume(Mode nextMode) {
        synchronized (pauseLock) {
            if (mode != Mode.PAUSED) {
                return;
            }
            stepFrame = null;
            targetFrame = null;
            pauseOnNextLine = false;
            mode = nextMode;
            pauseLock.notifyAll();
        }
    }

    /**
     * Called by the Jython trace adapter on the script's worker thread. The adapter must return itself as the local trace function.
     *
     * @param frame Jython frame for this event
     * @param event typically call, line, return, or exception
     * @param arg event-specific argument
     */
    public void trace(PyObject frame, String event, PyObject arg) {
        if (!(frame instanceof PyFrame)) {
            return;
        }
        PyFrame pyFrame = (PyFrame) frame;
        boolean shouldPause = false;
        int line = -1;

        synchronized (pauseLock) {
            if (mode == Mode.STOPPED) {
                throw new DebuggerStoppedException();
            }

            if ("return".equals(event)) {
                if ((mode == Mode.STEP_OVER || mode == Mode.STEP_OUT)
                        && pyFrame == stepFrame) {
                    // The stepped frame is ending. Resume in its caller.
                    targetFrame = pyFrame.f_back;
                    stepFrame = null;
                    pauseOnNextLine = true;
                }
                return;
            }

            if (!"line".equals(event)) {
                return;
            }

            line = pyFrame.f_lineno;
            PyObject code = pyFrame.__getattr__("f_code");
            String filename = code.__getattr__("co_filename").toString();
            boolean breakpoint = breakpoints.contains(line);

            listener.tracedLine(event, line);
            
            if (breakpoint) {
                shouldPause = true;
            } else if (mode == Mode.STEP_INTO) {
                shouldPause = true;
            } else if (mode == Mode.STEP_OVER) {
                shouldPause = (pyFrame == stepFrame)
                        || (pauseOnNextLine && pyFrame == targetFrame);
            } else if (mode == Mode.STEP_OUT) {
                shouldPause = pauseOnNextLine && pyFrame == targetFrame;
            }
        }

        if (shouldPause) {
            pauseAtLine(pyFrame, line);
        }
        
    }

    private void pauseAtLine(PyFrame frame, int line) {
        synchronized (pauseLock) {
            if (mode == Mode.STOPPED) {
                throw new DebuggerStoppedException();
            }
            currentFrame = frame;
            stepFrame = null;
            targetFrame = null;
            pauseOnNextLine = false;
            mode = Mode.PAUSED;
        }
        
        PyObject code = frame.__getattr__("f_code");
        String filename = code.__getattr__("co_filename").toString();
        Listener l = listener;
        if (l != null) {
            l.pausedAtLine(filename,line);
        }

        synchronized (pauseLock) {
            while (mode == Mode.PAUSED) {
                try {
                    pauseLock.wait();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    mode = Mode.STOPPED;
                    break;
                }
            }
            if (mode == Mode.STOPPED) {
                throw new DebuggerStoppedException();
            }
        }

        l = listener;
        if (l != null) {
            l.resumed();
        }
    }

    /**
     * Called by the execution worker in its finally block.
     */
    public void finished() {
        synchronized (pauseLock) {
            mode = Mode.STOPPED;
            currentFrame = null;
            stepFrame = null;
            targetFrame = null;
            pauseLock.notifyAll();
        }
        Listener l = listener;
        if (l != null) {
            l.finished();
        }
    }

    /**
     * Internal control-flow exception used to abort execution on stop().
     */
    public static final class DebuggerStoppedException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public DebuggerStoppedException() {
            super("Script execution stopped by debugger");
        }
    }
}
