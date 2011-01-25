package org.autoplot.pngwalk;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.net.URL;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
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

    protected static final BufferedImage loadingImage = initLoadingImage();
    protected static final ImageIcon okBadge;
    protected static final ImageIcon problemBadge;
    protected static final ImageIcon ignoreBadge;

    static {
        URL u = PngWalkView.class.getResource("/resources/badge_problem.png");
        if (u != null)
            problemBadge = new ImageIcon(u);
        else
            problemBadge = null;

        u = PngWalkView.class.getResource("/resources/badge_ok.png");
        if (u != null)
            okBadge = new ImageIcon(u);
        else
            okBadge = null;

        u = PngWalkView.class.getResource("/resources/badge_ignore.png");
        if (u != null)
            ignoreBadge = new ImageIcon(u);
        else
            ignoreBadge = null;
    }

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
     * (or <code>sequencChanged()</code> if appropriate) but subclasses may override.
     * @param e
     */
    public void propertyChange(PropertyChangeEvent e) {
        if (e.getPropertyName().equals(WalkImageSequence.PROP_SEQUENCE_CHANGED)) {
            sequenceChanged();
        } else {
            repaint();
        }
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

    /**
     * return the component that will generate mouse events.  Some
     * components have a JScrollPane, so simply adding a listener to the 
     * PngWalkView doesn't work.  The base class implementation of this 
     * simply returns the PngWalkView, but such components should override
     * this method.
     * @return
     */
    public JComponent getMouseTarget() {
        return this;
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
        FontMetrics fm = this.getFontMetrics(this.getFont());
        double captionHeight=  ( showCaptions && caption!=null ) ? ( fm.getHeight() + fm.getDescent() ) : 0 ;
        double imageHeight= i.getHeight();
        double xfactor = (double) getWidth() / (double) i.getWidth(null);
        double yfactor = (double) ( getHeight()-captionHeight ) / (double) imageHeight;
        double s = Math.min(xfactor, yfactor);
        s = Math.min(1.0, s);
        int xpos = (int) (this.getWidth() - i.getWidth(null) * s) / 2;
        int ypos = (int) ((this.getHeight()-captionHeight) - imageHeight * s) / 2;
        int xs = (int) (i.getWidth(null) * s);
        int ys = (int) (i.getHeight(null) * s);
        BufferedImageOp resizeOp = new ScalePerspectiveImageOp(i.getWidth(), i.getHeight(), 0, 0, xs, ys, 0, -1, -1, 0, false);
        g2.drawImage(i, resizeOp, xpos, ypos);
        if ( showCaptions && caption != null) {
            int cx = xpos;
            int cy = ypos + ys + fm.getHeight();
            g2.drawString(caption, cx, cy);
        }
        if (PngWalkTool1.isQualityControlEnabled() && seq.getQualityControlSequence()!=null ) {
            paintQualityControlIcon( seq.getIndex(), g2, xpos, ypos, true );
        }
    }

    private static BufferedImage initLoadingImage() {
        BufferedImage li;
        //e.printStackTrace(System.err);
        // Construct a backup image to use
        li = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = li.createGraphics();
        g2.addRenderingHints(new RenderingHints(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON));
        g2.setColor(new java.awt.Color(0.0F, 0.0F, 0.0F, 0.5F));
        g2.fillRoundRect(0, 0, 48, 48, 6, 6);
        //TODO: Add text or hourglass or something?
        g2.setColor(java.awt.Color.WHITE);
        g2.fillOval(12, 32, 4, 4);
        g2.fillOval(24, 32, 4, 4);
        g2.fillOval(36, 32, 4, 4);
        return li;
    }

    protected void paintQualityControlIcon(int i, Graphics2D g2, int imgX, int imgY, boolean icon) {
        QualityControlRecord rec = seq.getQualityControlSequence().getQualityControlRecord(i);
        if ( okBadge==null ) {
            throw new RuntimeException("unable to locate all badges for quality control");
        }
        if (rec != null) {
            // "missing" images
            if ( icon ) {
                switch (rec.getStatus()) {
                    case OK:
                        okBadge.paintIcon(this, g2, imgX + 5, imgY + 5);
                        break;
                    case PROBLEM:
                        problemBadge.paintIcon(this, g2, imgX + 5, imgY + 5);
                        break;
                    case IGNORE:
                        ignoreBadge.paintIcon(this, g2, imgX + 5, imgY + 5);
                    default:
                        // Don't do anything.
                        return;
                }
            } else {
                Color color0= g2.getColor();
                switch (rec.getStatus()) {
                    case OK:
                        System.err.println("imgX="+imgX);
                        g2.setColor( Color.GREEN );
                        break;
                    case PROBLEM:
                        g2.setColor( Color.RED );
                        break;
                    case IGNORE:
                        g2.setColor( Color.GRAY );
                    default:
                        // Don't do anything.
                        return;
                }
                g2.fillOval( imgX, imgY, 6, 6 );
                g2.setColor( Color.GRAY );
                g2.drawOval( imgX, imgY, 6, 6 );
                g2.setColor(color0);
            }
        }
    }

}
