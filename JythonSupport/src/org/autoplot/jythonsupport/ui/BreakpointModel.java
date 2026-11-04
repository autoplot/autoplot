package org.autoplot.jythonsupport.ui;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;

public class BreakpointModel {

    private final Document document;
    
    private final List<Breakpoint> breakpoints = new ArrayList<>();
    
    private final PropertyChangeSupport pcs =
            new PropertyChangeSupport(this);

    public BreakpointModel( Document document ) {
        this.document= document;
    }
    
    public boolean contains(int line) {
        return getBreakpoint(line)!=null;
    }

    public Breakpoint getBreakpoint(int line) {
        for (Breakpoint bp : breakpoints ) {
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

        Breakpoint bp= getBreakpoint(line);
        if ( bp!=null ) {
            breakpoints.remove(bp);
            return;
        }
        
        breakpoints.add( new Breakpoint( document, line ) );

        pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
    }
    
    private Position getPosition(int line) throws BadLocationException {        
        //for ( Position r: breakpoints.keySet() ) {
         
        //}
        Element root = document.getDefaultRootElement();
        int offset = root.
                getElement(line-1).
                getStartOffset();
        return document.createPosition(offset);
    }
    public void set(int line) throws BadLocationException {
        
        Breakpoint bp= getBreakpoint(line);
        
        if ( bp==null ) {
            breakpoints.add(new Breakpoint(document,line));

            pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
        }
    }
    
    public void clear(int line) throws BadLocationException {

        Breakpoint bp= getBreakpoint(line);
        
        if ( bp!=null ) {
            breakpoints.remove(bp);

            pcs.firePropertyChange(
                "breakpoints", null, getBreakpoints());
        }
    }

    public Set<Breakpoint> getBreakpoints() {
        return new TreeSet<>(breakpoints);
    }

    /**
     * return the first breakpoint with this line
     * @param line
     * @return 
     */
    public Breakpoint getBreakpoint(Position line) {
        for ( Breakpoint bp : breakpoints ) {
            if ( bp.position.getOffset()==line.getOffset() ) {
                return bp;
            }
        }
        return null;
    }
    
    public void addPropertyChangeListener(
            PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }
}