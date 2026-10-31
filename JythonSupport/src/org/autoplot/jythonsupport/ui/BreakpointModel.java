package org.autoplot.jythonsupport.ui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Set;
import java.util.TreeSet;

public class BreakpointModel {

    private final Set<Integer> lines = new TreeSet<>();

    private final PropertyChangeSupport pcs =
            new PropertyChangeSupport(this);

    public boolean contains(int line) {
        return lines.contains(line);
    }

    public void toggle(int line) {
        if (lines.contains(line)) {
            lines.remove(line);
        } else {
            lines.add(line);
        }

        pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
    }
    
    public void set(int line) {
        if (!lines.contains(line)) {
            lines.add(line);

            pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
        }
    }
    
    public void clear(int line) {
        if (lines.contains(line)) {
            lines.remove(line);

            pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
        }
    }

    public Set<Integer> getBreakpoints() {
        return new TreeSet<>(lines);
    }

    public void addPropertyChangeListener(
            PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }
}