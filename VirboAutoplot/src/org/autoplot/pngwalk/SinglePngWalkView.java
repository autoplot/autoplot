package org.autoplot.pngwalk;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

/**
 * An implementation of PngWalkView to display a single image.
 * @author Ed Jackson
 */
public class SinglePngWalkView extends PngWalkView {

//    private boolean sizeValid = false;
    private BufferedImage cacheImage;

    public SinglePngWalkView(WalkImageSequence s) {
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
}
