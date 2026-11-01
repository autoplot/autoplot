package org.autoplot.jythonsupport.ui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;

public class BreakpointModel {

    private final Document document;
    
    private final Map<Position,Breakpoint> breakpoints = new HashMap<>();

    private final PropertyChangeSupport pcs =
            new PropertyChangeSupport(this);

    public BreakpointModel( Document document ) {
        this.document= document;
    }
    
    public boolean contains(int line) {
        return getBreakpoint(line)!=null;
    }

    public Breakpoint getBreakpoint(int line) {
        for (Breakpoint bp : breakpoints.values()) {
            if (bp.getLine() == line) {
                return bp;
            }
        }
        return null;
    }
    
    public void toggle(int line) throws BadLocationException {
        
        Element root = document.getDefaultRootElement();

        if (line < 1 || line > root.getElementCount()) {
            throw new IllegalArgumentException("Invalid line: " + line);
        }

        // Line numbers are 1-based; document elements are 0-based.
        int offset = root.getElement(line - 1).getStartOffset();

        for ( Entry<Position,Breakpoint> ibp: breakpoints.entrySet() ) {
            Position p = ibp.getValue().getPosition();

            if (root.getElementIndex(p.getOffset()) == line - 1) {
                breakpoints.remove(ibp.getKey());
                return;
            }
        }

        Position p= document.createPosition(offset);
        breakpoints.put( p, new Breakpoint( document, line ) );

        pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
    }
    
    private Position getPosition(int line) throws BadLocationException {
        Element root = document.getDefaultRootElement();
        int offset = root.
                getElement(line-1).
                getStartOffset();
        return document.createPosition(offset);
    }
    public void set(int line) throws BadLocationException {
        Position p= getPosition(line);
        if (!breakpoints.containsKey(p)) {
            breakpoints.put(p,new Breakpoint(document,line));

            pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
        }
    }
    
    public void clear(int line) throws BadLocationException {
        Position p= getPosition(line);
        if (breakpoints.containsKey(p)) {
            breakpoints.remove(p);

            pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
        }
    }

    public Set<Breakpoint> getBreakpoints() {
        return new TreeSet<>(breakpoints.values());
    }

    /**
     * return the first breakpoint with this line
     * @param line
     * @return 
     */
    public Breakpoint getBreakpoint(Position line) {
        return breakpoints.get(line);
    }
    
    public void addPropertyChangeListener(
            PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }
}