
package org.autoplot.jythonsupport.ui;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import jsyntaxpane.components.LineNumbersRuler;
import org.das2.util.LoggerManager;

/**
 * extend the LineNumberRuler to add breakpoints and current program counter.
 * @author jbf
 */
public class DebugLineNumbersRuler extends LineNumbersRuler {

    private static final Logger logger= LoggerManager.getLogger("jython.debugger");
    
    public static BreakpointModel NO_BREAKPOINTS= new BreakpointModel();
    private BreakpointModel breakpoints= NO_BREAKPOINTS;
    
    private int programCounter = -1;

    BufferedImage pc;
    BufferedImage breakpoint;
    
    private JPopupMenu lineNumbersPopup;
    int selectedLine=-1;
    
    public DebugLineNumbersRuler() {
        try {
            pc = ImageIO.read( this.getClass().getResource("/resources/pc_16.png") );
            breakpoint= ImageIO.read( this.getClass().getResource("/resources/breakpoint_16.png") );
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        
        lineNumbersPopup= new JPopupMenu();
        lineNumbersPopup.add( new JMenuItem( new AbstractAction("Set Breakpoint") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int line= selectedLine;
                setBreakpoint(line,true);
            }
        }));
        lineNumbersPopup.add( new JMenuItem( new AbstractAction("Clear Breakpoint") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int line= selectedLine;
                setBreakpoint(line,false);
            }
        }));
        super.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                FontMetrics fontMetrics = getFontMetrics(getFont());
                int lh = fontMetrics.getHeight();
                selectedLine= (int)(e.getY()/lh+1);
                if ( e.isPopupTrigger() ) {
                    lineNumbersPopup.show(DebugLineNumbersRuler.this,e.getX(),e.getY());
                }
                super.mousePressed(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                FontMetrics fontMetrics = getFontMetrics(getFont());
                int lh = fontMetrics.getHeight();
                selectedLine= (int)(e.getY()/lh+1);
                if ( e.isPopupTrigger() ) {
                    lineNumbersPopup.show(DebugLineNumbersRuler.this,e.getX(),e.getY());
                }
                super.mouseReleased(e);
            }
            
        });
    }
    /**
     * set the line number, 1 being the first line.  -1 will turn off.
     * @param line 
     */
    public void setProgramCounter(int line) {
        programCounter = line;
        repaint();
    }

    public void setBreakpoint(int line, boolean enabled) {
        if (enabled) {
            this.breakpoints.set(line);
        } else {
            this.breakpoints.clear(line);
        }
        repaint();
    }
    
    
    public void setBreakpointModel(BreakpointModel model) {
        this.breakpoints = model;

        model.addPropertyChangeListener(evt -> repaint());

        repaint();
    }
    
    public BreakpointModel getBreakpointModel() {
        return this.breakpoints;
    }
            
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        FontMetrics fontMetrics = getFontMetrics(getFont());
        int lh = fontMetrics.getHeight();
        Graphics2D g2= (Graphics2D)g;
        for ( Integer i : breakpoints.getBreakpoints() ) {
            g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
            g2.drawImage( breakpoint, 0, lh*(i-1), this );
        }
        g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g2.drawImage( pc, 0, lh*(programCounter-1), this );
    }

    @Override
    public synchronized void addMouseListener(MouseListener l) {
        super.addMouseListener(l); 
    }
    
    
    
}
