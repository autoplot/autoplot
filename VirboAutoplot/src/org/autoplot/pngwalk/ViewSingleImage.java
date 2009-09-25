package org.autoplot.pngwalk;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;

/**
 * An implementation of PngWalkView to display a single image.
 * @author Ed Jackson
 */
public class ViewSingleImage extends PngWalkView {

//    private boolean sizeValid = false;
    private Image cacheImage;

    public ViewSingleImage(WalkImageSequence s) {
        super(s);

        addMouseWheelListener(new MouseAdapter() {

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                seq.skipBy(e.getWheelRotation());
            }
        });
    }

    @Override
    protected synchronized void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g2 = (Graphics2D) g1;

        //drawCenteredString(g2, "This is a long string of nonsense.");
        Image i = seq.currentImage().getImage();

        if (i.getWidth(this) > 0 && i.getHeight(this) > 0) {
            paintImageCentered(i, g2);
            cacheImage = i;
        } else if (cacheImage != null) {
            paintImageCentered(cacheImage, g2);
        }

    }

    private void paintImageCentered(Image i, Graphics g) {
            double xfactor = (double) getWidth() / (double) i.getWidth(null);
            double yfactor = (double) getHeight() / (double) i.getHeight(null);
            double s = Math.min(xfactor, yfactor);
            s = Math.min(1.0, s);

            //TODO: update to use ScalePerspectiveImageOp
            //AffineTransformOp resizeOp = new AffineTransformOp(AffineTransform.getScaleInstance(s, s), AffineTransformOp.TYPE_BILINEAR);
            //AffineTransform xform = AffineTransform.getScaleInstance(s,s);

            int xpos = (int)(this.getWidth() - i.getWidth(null)*s) / 2;
            int ypos = (int)(this.getHeight() - i.getHeight(null)*s) / 2;
            int xs = (int)(i.getWidth(null)*s);
            int ys = (int)(i.getHeight(null)*s);
            //xform.translate(xpos, ypos);

            g.drawImage(i, xpos, ypos, xs, ys, this);

    }
}
