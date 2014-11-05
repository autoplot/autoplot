package org.autoplot.pngwalk;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.virbo.dataset.QDataSet;

/**
 * An implementation of PngWalkView to display a single image.
 * @author Ed Jackson
 */
public class SinglePngWalkView extends PngWalkView {

//    private boolean sizeValid = false;
    private transient BufferedImage cacheImage;

    Rectangle imageLocation= null;
    
    ClickDigitizer clickDigitizer;
    
    public SinglePngWalkView(WalkImageSequence s) {
        super(s);
        clickDigitizer= new ClickDigitizer( this );
        
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
            
            @Override
            public void mouseClicked(MouseEvent e) { 
                Rectangle lrect= imageLocation;
                if ( imageLocation==null ) return;
                BufferedImage i = seq.currentImage().getImage();
                if ( i==null ) return;
                double factor = (double) lrect.getWidth() / (double) i.getWidth(null);
                
                int imageX= (int)( ( e.getX() - lrect.x ) / factor );
                int imageY= (int)( ( e.getY() - lrect.y ) / factor );
                
                try {
                    clickDigitizer.doLookupMetadata( imageX, imageY );
                } catch ( IOException ex ) {
                    logger.log(Level.SEVERE, null, ex);
                } catch (ParseException ex) {
                    logger.log(Level.SEVERE, null, ex);
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
            imageLocation= paintImageCentered(i, g2, seq.currentImage().getCaption());
            cacheImage = i;
        } else {
            if (cacheImage != null) {
                imageLocation= paintImageCentered(cacheImage, g2, seq.currentImage().getCaption());
            }
            paintImageCentered(loadingImage, g2);
        }

        if ( clickDigitizer.viewer!=null && clickDigitizer.viewer.digitizer!=null ) {
            try {
                QDataSet ids= clickDigitizer.doTransform( );
                if ( ids!=null ) {
                    for ( int j=0; j<ids.length(); j++ ) {
                        int ix= (int) ids.value(j,0);
                        int iy= (int) ids.value(j,1);
                        Rectangle lrect= imageLocation;
                        if ( imageLocation==null ) return;

                        if ( i==null ) return;
                        double factor = (double) lrect.getWidth() / (double) i.getWidth(null);

                        int imageX= (int)( ( ix + lrect.x ) * factor );
                        int imageY= (int)( ( iy + lrect.y ) * factor );                    
                        g2.drawLine( 0,100, imageY,imageY);
                        g2.drawLine( imageX,imageX,0,100 );
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
