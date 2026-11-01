package org.autoplot.jythonsupport.ui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.autoplot.jythonsupport.ui.JythonDebugger.Breakpoint;

public class BreakpointModel {

    private final Map<Integer,Breakpoint> lines = new HashMap<>();

    private final PropertyChangeSupport pcs =
            new PropertyChangeSupport(this);

    public boolean contains(int line) {
        return lines.containsKey(line);
    }

    public void toggle(int line) {
        if (lines.containsKey(line)) {
            lines.remove(line);
        } else {
            lines.put(line,new Breakpoint(line));
        }

        pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
    }
    
    public void set(int line) {
        if (!lines.containsKey(line)) {
            lines.put(line,new Breakpoint(line));

            pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
        }
    }
    
    public void clear(int line) {
        if (lines.containsKey(line)) {
            lines.remove(line);

            pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
        }
    }

    public Set<Breakpoint> getBreakpoints() {
        return new TreeSet<>(lines.values());
    }

    /**
     * return the first breakpoint with this line
     * @param line
     * @return 
     */
    public Breakpoint getBreakpoint(int line) {
        return lines.get(line);
    }
    
    public void addPropertyChangeListener(
            PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }
}