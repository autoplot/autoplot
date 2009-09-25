package org.autoplot.pngwalk;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JPanel;

/**
 * This is the abstract superclass for views in the PNGWalk tool.  Concrete
 * subclasses will do the work of actually laying out a particular view and
 * handling events on it.
 *
 * @author Ed Jackson
 */
public abstract class PngWalkView extends JPanel implements PropertyChangeListener {

    protected WalkImageSequence seq;
    //protected boolean showMissing = false;  //Should view show placeholder for missing files?

    protected PngWalkView(WalkImageSequence sequence) {
        super(true);
        seq = sequence;
        seq.addPropertyChangeListener(this);
    }

    /** Respond to property changes on the {@list WalkImageSequence} this view
     * represents.  The default implementation just calls <code>repaint()</code>,
     * but subclasses may override.
     * @param e
     */
    public void propertyChange(PropertyChangeEvent e) {
        repaint();
    }

    // Add code for managing image sequence
    // Caption painting
    // Missing image painting

    // Error message painting (e.g. "No matching images")
    protected  void drawCenteredString(Graphics2D g, String msg) {
        if (msg == null) {
            return;
        }
        System.err.println("draw string");
        Rectangle bounds = g.getClipBounds();
        if (bounds != null) {
            FontMetrics fm = this.getFontMetrics(this.getFont());
            g.drawString(msg, (bounds.width - fm.stringWidth(msg)) / 2, bounds.height / 2);
        } else {
            System.err.println("bad clipping");
        }

    }

    /** Scale an image to the largest size that will fit in the given dimensions,
     * but never larger than the original.
     * @param im
     * @param maxWidth
     * @param maxHeight
     * @return
     */
    protected static BufferedImage scaleImage(BufferedImage im, int maxWidth, int maxHeight) {
        double xfactor = (double) maxWidth / (double) im.getWidth();
        double yfactor = (double) maxHeight / (double) im.getHeight();
        double s = Math.min(xfactor, yfactor);
        s = Math.min(1.0, s);
        System.err.println("Scale factor = " + s);
        AffineTransformOp resizeOp = new AffineTransformOp(AffineTransform.getScaleInstance(s, s), AffineTransformOp.TYPE_BILINEAR);
        return resizeOp.filter(im, null);
    }
  
}
