package org.autoplot.pngwalk;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/**
 * An implementation of PngWalkView to display a single image.
 * @author Ed Jackson
 */
public class SinglePngWalkView extends PngWalkView {

//    private boolean sizeValid = false;
    private transient BufferedImage cacheImage;

    public SinglePngWalkView(WalkImageSequence s) {
        super(s);
        setShowCaptions(true);
        addMouseWheelListener( getMouseWheelListener() );
        addMouseListener( new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if ( e.isPopupTrigger() ) {
                    getPopup().show(e.getComponent(),e.getX(), e.getY());
                }
            }

            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                if ( e.isPopupTrigger() ) {
                    getPopup().show(e.getComponent(),e.getX(), e.getY());
                }
            }
        } );

    }

    @Override
    protected synchronized void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g2 = (Graphics2D) g1;

        g2.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON );
        g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

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
