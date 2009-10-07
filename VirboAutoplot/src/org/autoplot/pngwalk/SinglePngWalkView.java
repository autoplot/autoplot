package org.autoplot.pngwalk;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
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
        setShowCaptions(true);
        addMouseWheelListener(new MouseWheelListener() {
            public void mouseWheelMoved(MouseWheelEvent e) {
                if (seq!=null && seq.size()!=0 ) seq.skipBy(e.getWheelRotation());
            }
        });
    }

    @Override
    protected synchronized void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g2 = (Graphics2D) g1;

        if (seq == null || seq.size()==0) return;

        BufferedImage i = seq.currentImage().getImage();

        if (i!=null && i.getWidth(this) >0 && i.getHeight(this) > 0) {
            paintImageCentered(i, g2, seq.currentImage().getCaption());
            cacheImage = i;
        } else {
            if (cacheImage != null)
                paintImageCentered(cacheImage, g2, seq.currentImage().getCaption());
            paintImageCentered(loadingImage, g2);
        }

    }
}
