
package org.autoplot.jythonsupport.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.View;

/*
 *  Draws a wedge at the position indicating lines were deleted.
 */
public class DeletePainter extends DefaultHighlighter.DefaultHighlightPainter
{
	public DeletePainter(Color color)
	{
		super( color );
	}

	/**
	 * Paints a portion of a highlight.
	 *
	 * @param  g1 the graphics context
	 * @param  offs0 the starting model offset >= 0
	 * @param  offs1 the ending model offset >= offs1
	 * @param  bounds the bounding box of the view, which is not
	 *	       necessarily the region to paint.
	 * @param  c the editor
	 * @param  view View painting for
	 * @return region drawing occured in
	 */
    @Override
	public Shape paintLayer(Graphics g1, int offs0, int offs1, Shape bounds, JTextComponent c, View view)
	{
		Rectangle r = getDrawingArea(offs0, offs1, bounds, view);

		if (r == null) return null;

        Graphics2D g= (Graphics2D)g1;
        g.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        
		//  Do your custom painting

		Color color = getColor();
		g.setColor(color == null ? c.getSelectionColor() : color);

        Graphics2D g2= (Graphics2D)g;
        g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        
        Shape poly= new Polygon( new int[] { r.x, r.x+5, r.x, r.x }, new int[] { r.y-5, r.y, r.y+5, r.y-5 }, 4 );
        g.fill( poly ); 

		// Return the drawing area

		return poly;
	}


	private Rectangle getDrawingArea(int offs0, int offs1, Shape bounds, View view)
	{
		// Contained in view, can just use bounds.

		if (offs0 == view.getStartOffset() && offs1 == view.getEndOffset())
		{
			Rectangle alloc;

			if (bounds instanceof Rectangle)
			{
				alloc = (Rectangle)bounds;
			}
			else
			{
				alloc = bounds.getBounds();
			}

			return alloc;
		}
		else
		{
			// Should only render part of View.
			try
			{
				// --- determine locations ---
				Shape shape = view.modelToView(offs0, Position.Bias.Forward, offs1,Position.Bias.Backward, bounds);
				Rectangle r = (shape instanceof Rectangle) ? (Rectangle)shape : shape.getBounds();

				return r;
			}
			catch (BadLocationException e)
			{
				// can't render
			}
		}

		// Can't render

		return null;
	}
}