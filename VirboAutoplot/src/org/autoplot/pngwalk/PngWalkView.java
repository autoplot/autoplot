package org.autoplot.pngwalk;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
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
    protected boolean showCaptions = false;
    //protected boolean showMissing = false;  //Should view show placeholder for missing files?

    protected static BufferedImage loadingImage = initLoadingImage();

    protected PngWalkView(WalkImageSequence sequence) {
        setSequence(sequence);
    }

    public final void setSequence(WalkImageSequence sequence) {
        if (seq != null) seq.removePropertyChangeListener(this);
        seq = sequence;
        if (seq != null) {
            seq.addPropertyChangeListener(this);
        }
        sequenceChanged();
    }

    public WalkImageSequence getSequence() {
        return seq;
    }

    /**
     * Subclasses should override this method if they need to do anything special
     * when the view gets a new image sequence.  The default implementation just
     * calls <code>repaint()</code>.
     * @param seq
     */
    protected void sequenceChanged() {
        repaint();
    }

    /**
     * subclasses should override this if they need to take action when the
     * thumbnail size changes.
     */
    protected void thumbnailSizeChanged() {
        repaint();
    }

    /** Respond to property changes on the {@list WalkImageSequence} this view
     * represents.  The default implementation just calls <code>repaint()</code>,
     * but subclasses may override.
     * @param e
     */
    public void propertyChange(PropertyChangeEvent e) {
        repaint();
    }

    public boolean isShowCaptions() {
        return showCaptions;
    }

    public void setShowCaptions(boolean showCaptions) {
        this.showCaptions = showCaptions;
    }


    protected int thumbnailSize = 200;
    public static final String PROP_THUMBNAILSIZE = "thumbnailSize";

    public int getThumbnailSize() {
        return thumbnailSize;
    }

    public void setThumbnailSize(int thumbnailSize) {
        int oldThumbnailSize = this.thumbnailSize;
        this.thumbnailSize = thumbnailSize;
        thumbnailSizeChanged();
        firePropertyChange(PROP_THUMBNAILSIZE, oldThumbnailSize, thumbnailSize);
    }

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

    protected void paintImageCentered(BufferedImage i, Graphics2D g2) {
        paintImageCentered(i, g2, null);
    }

    protected void paintImageCentered(BufferedImage i, Graphics2D g2, String caption) {
        double xfactor = (double) getWidth() / (double) i.getWidth(null);
        double yfactor = (double) getHeight() / (double) i.getHeight(null);
        double s = Math.min(xfactor, yfactor);
        s = Math.min(1.0, s);
        int xpos = (int) (this.getWidth() - i.getWidth(null) * s) / 2;
        int ypos = (int) (this.getHeight() - i.getHeight(null) * s) / 2;
        int xs = (int) (i.getWidth(null) * s);
        int ys = (int) (i.getHeight(null) * s);
        BufferedImageOp resizeOp = new ScalePerspectiveImageOp(i.getWidth(), i.getHeight(), 0, 0, xs, ys, 0, -1, -1, 0, false);
        g2.drawImage(i, resizeOp, xpos, ypos);
        if ( showCaptions && caption != null) {
            FontMetrics fm = this.getFontMetrics(this.getFont());
            int cx = xpos;
            int cy = ypos + ys + fm.getHeight();
            g2.drawString(caption, cx, cy);
        }
    }

    private static BufferedImage initLoadingImage() {
        BufferedImage li = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = li.createGraphics();
        g2.addRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
        g2.setColor(new java.awt.Color(0.0F, 0.0F, 0.0F, 0.5F));
        g2.fillRoundRect(0, 0, 80, 80, 10, 10);
        //TODO: Add text or hourglass or something?
        g2.setColor(java.awt.Color.WHITE);
        g2.fillOval(16, 54, 8, 8);
        g2.fillOval(36, 54, 8, 8);
        g2.fillOval(56, 54, 8, 8);
        return li;
    }
  
}
