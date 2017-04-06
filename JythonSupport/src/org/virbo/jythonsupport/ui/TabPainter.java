package org.virbo.jythonsupport.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.View;

/*
 *  From http://tips4java.wordpress.com/2008/10/28/rectangle-painter/
 * 
 *  Implements a simple highlight painter that renders a rectangle around the
 *  area to be highlighted.
 *
 */
public class TabPainter extends DefaultHighlighter.DefaultHighlightPainter {

    public TabPainter(Color color) {
        super(color);
    }

    /**
     * Paints a portion of a highlight.
     *
     * @param g1 the graphics context
     * @param offs0 the starting model offset >= 0
     * @param offs1 the ending model offset >= offs1
     * @param bounds the bounding box of the view, which is not necessarily the
     * region to paint.
     * @param c the editor
     * @param view View painting for
     * @return region drawing occured in
     */
    @Override
    public Shape paintLayer(Graphics g1, int offs0, int offs1, Shape bounds, JTextComponent c, View view) {
        
        for ( int offs= offs0; offs<offs1+1; offs++ ) {
            Rectangle r = getDrawingArea(offs, offs+1, bounds, view);

            if (r == null) {
                continue;
            }

            Graphics2D g = (Graphics2D) g1;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            //  Do your custom painting
            Color color = getColor();
            g.setColor(color == null ? c.getSelectionColor() : color);

            int y = r.y + r.height/2;
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
            float hx= r.x+r.width-3;
            float hy= y;
            float dx= 3;
        
            GeneralPath p= new GeneralPath();
            p.moveTo( hx, hy );
        
            p.lineTo( (hx-2*dx), (hy-dx-1) );
            p.lineTo( (hx-2*dx), (hy+dx+1) );
            p.lineTo( hx, hy );            
            g.fill( p );
        
            g.drawLine( r.x, y, (int)hx-2, y );
            
            g.setFont( Font.decode("sans-8") );
            g.drawString( String.valueOf(offs), r.x, hy );
        }
        
        Rectangle r = getDrawingArea(offs0, offs1, bounds, view);
        
        // Return the drawing area
        return r;
    }

    private Rectangle getDrawingArea(int offs0, int offs1, Shape bounds, View view) {
        // Contained in view, can just use bounds.

        if (offs0 == view.getStartOffset() && offs1 == view.getEndOffset()) {
            Rectangle alloc;

            if (bounds instanceof Rectangle) {
                alloc = (Rectangle) bounds;
            } else {
                alloc = bounds.getBounds();
            }

            return alloc;
        } else {
            // Should only render part of View.
            try {
                // --- determine locations ---
                Shape shape = view.modelToView(offs0, Position.Bias.Forward, offs1, Position.Bias.Backward, bounds);
                Rectangle r = (shape instanceof Rectangle) ? (Rectangle) shape : shape.getBounds();

                return r;
            } catch (BadLocationException e) {
                // can't render
            }
        }

        // Can't render
        return null;
    }
}
