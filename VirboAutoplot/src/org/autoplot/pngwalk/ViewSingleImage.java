package org.autoplot.pngwalk;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;

/**
 * An implementation of PngWalkView to display a single image.
 * @author Ed Jackson
 */
public class ViewSingleImage extends PngWalkView {

//    private boolean sizeValid = false;
    private BufferedImage cacheImage;

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
        BufferedImage i = seq.currentImage().getImage();

        if (i!=null && i.getWidth(this) >0 && i.getHeight(this) > 0) {
            paintImageCentered(i, g2);
            cacheImage = i;
        } else {
            if (cacheImage != null)
                paintImageCentered(cacheImage, g2);
            paintImageCentered(WalkImage.LOADING_IMAGE, g2);
        }

    }

    private void paintImageCentered(BufferedImage i, Graphics2D g2) {
            double xfactor = (double) getWidth() / (double) i.getWidth(null);
            double yfactor = (double) getHeight() / (double) i.getHeight(null);
            double s = Math.min(xfactor, yfactor);
            s = Math.min(1.0, s);

            int xpos = (int)(this.getWidth() - i.getWidth(null)*s) / 2;
            int ypos = (int)(this.getHeight() - i.getHeight(null)*s) / 2;
            int xs = (int)(i.getWidth(null)*s);
            int ys = (int)(i.getHeight(null)*s);

           BufferedImageOp resizeOp = new ScalePerspectiveImageOp(i.getWidth(), i.getHeight(), 0, 0, xs, ys, 0 ,0, false);

            g2.drawImage(i, resizeOp, xpos, ypos);
    }
}
