
package org.autoplot.jythonsupport.ui;

import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import jsyntaxpane.components.LineNumbersRuler;
import org.das2.util.LoggerManager;

/**
 * extend the LineNumberRuler to add breakpoints and current program counter.
 * @author jbf
 */
public class DebugLineNumbersRuler extends LineNumbersRuler {

    private static final Logger logger= LoggerManager.getLogger("jython.debugger");
    
    private final Set<Integer> breakpoints = new HashSet<>();
    private int programCounter = -1;

    BufferedImage pc;
    BufferedImage breakpoint;

    public DebugLineNumbersRuler() {
        try {
            pc = ImageIO.read( this.getClass().getResource("/resources/pc_16.png") );
            breakpoint= ImageIO.read( this.getClass().getResource("/resources/breakpoint_16.png") );
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
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
            breakpoints.add(line);
        } else {
            breakpoints.remove(line);
        }
        repaint();
    }
            
    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        FontMetrics fontMetrics = getFontMetrics(getFont());
        int lh = fontMetrics.getHeight();
        Graphics2D g2= (Graphics2D)g;
        for ( Integer i : breakpoints ) {
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
