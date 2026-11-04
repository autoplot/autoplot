
package org.autoplot.jythonsupport.ui;

import java.awt.Component;
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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.text.BadLocationException;
import javax.swing.text.PlainDocument;
import jsyntaxpane.components.LineNumbersRuler;
import org.das2.util.LoggerManager;

/**
 * extend the LineNumberRuler to add breakpoints and current program counter.
 * @author jbf
 */
public class DebugLineNumbersRuler extends LineNumbersRuler {

    private static final Logger logger= LoggerManager.getLogger("jython.debugger");
    
    public static BreakpointModel NO_BREAKPOINTS= new BreakpointModel(new PlainDocument());
    
    private BreakpointModel breakpoints= NO_BREAKPOINTS;
    
    private int programCounter = -1;

    BufferedImage pcImage;
    BufferedImage breakpointImage;
    BufferedImage disabledBreakpointImage;
    
    private JPopupMenu lineNumbersPopup;
    int selectedLine=-1;
    
    public DebugLineNumbersRuler(Component parent) {
        try {
            pcImage = ImageIO.read( this.getClass().getResource("/resources/pc_16.png") );
            breakpointImage= ImageIO.read( this.getClass().getResource("/resources/breakpoint_16.png") );
            disabledBreakpointImage= ImageIO.read( this.getClass().getResource("/resources/disabled_breakpoint_16.png") );
        } catch (IOException ex) {
            logger.log(Level.SEVERE, null, ex);
        }
        
        lineNumbersPopup= new JPopupMenu();
        
        JMenuItem setBreakpointMI= new JMenuItem( new AbstractAction("Set Breakpoint") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int line= selectedLine;
                setBreakpoint(line,true);
            }
        });
        lineNumbersPopup.add(setBreakpointMI);
        
        JMenuItem editBreakpointMI= new JMenuItem( new AbstractAction("Edit Breakpoint") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int line= selectedLine;
                Breakpoint bp= breakpoints.getBreakpoint(line);
                if ( bp==null ) return;
                BreakpointEditor edit= new BreakpointEditor();
                edit.setBreakpoint(bp);
                if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( parent, edit, "Edit Breakpoint", JOptionPane.OK_CANCEL_OPTION ) ) {
                    edit.resetBreakpoint(bp);
                    repaint();
                }
            }
        });
        lineNumbersPopup.add(editBreakpointMI);
        
        JMenuItem clearBreakpointMI= new JMenuItem( new AbstractAction("Clear Breakpoint") {
            @Override
            public void actionPerformed(ActionEvent e) {
                int line= selectedLine;
                setBreakpoint(line,false);
            }
        });     
        lineNumbersPopup.add( clearBreakpointMI );
        
        super.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                FontMetrics fontMetrics = getFontMetrics(getFont());
                int lh = fontMetrics.getHeight();
                selectedLine= (int)(e.getY()/lh+1);
                if ( e.isPopupTrigger() ) {
                    if ( getBreakpointModel().getBreakpoint(selectedLine)!=null ) {
                        setBreakpointMI.setEnabled(false);
                        editBreakpointMI.setEnabled(true);
                        clearBreakpointMI.setEnabled(true);
                    } else {
                        setBreakpointMI.setEnabled(true);
                        editBreakpointMI.setEnabled(false);
                        clearBreakpointMI.setEnabled(false);
                    }
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
        try {
            if (enabled) {
                this.breakpoints.set(line);
            } else {
                this.breakpoints.clear(line);
            }
            repaint();
        } catch ( BadLocationException ex ) {
            ex.printStackTrace();
        }
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
        
        int ymid = fontMetrics.getHeight() * 11/20; // offset to middle of line number
        int yoffs= ymid - 8;
                
        g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );        
        g2.drawImage(pcImage, 0, lh*(programCounter-1)+yoffs, this );
        for ( Breakpoint bp : breakpoints.getBreakpoints() ) {
            if ( bp.enabled ) {
                g2.drawImage(breakpointImage, 0, lh*(bp.getLine()-1)+yoffs, this );
            } else {
                g2.drawImage(disabledBreakpointImage, 0, lh*(bp.getLine()-1)+yoffs, this );
            }
        }
    }

    @Override
    public synchronized void addMouseListener(MouseListener l) {
        super.addMouseListener(l); 
    }
    
    
    
}
