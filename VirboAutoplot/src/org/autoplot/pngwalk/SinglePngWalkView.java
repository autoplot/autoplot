package org.autoplot.pngwalk;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.das2.graph.GraphUtil;
import org.virbo.dataset.QDataSet;

/**
 * An implementation of PngWalkView to display a single image.
 * @author Ed Jackson
 */
public class SinglePngWalkView extends PngWalkView {

//    private boolean sizeValid = false;
    private transient BufferedImage cacheImage;

    Rectangle imageLocation= null;
    
    transient ClickDigitizer clickDigitizer;
    int clickDigitizerSelect= -1;
    
    public SinglePngWalkView(WalkImageSequence s) {
        super(s);
        clickDigitizer= new ClickDigitizer( this );
        
        setShowCaptions(true);
        addMouseWheelListener( getMouseWheelListener() );
        MouseAdapter ma= new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if ( e.isPopupTrigger() ) {
                    getPopup().show(e.getComponent(),e.getX(), e.getY());
                }
                Point p= getImagePosition( e.getX(), e.getY() );
                if ( p!=null ) try {
                    clickDigitizerSelect= clickDigitizer.maybeSelect(p);
                } catch (IOException ex) {
                    Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                } catch (ParseException ex) {
                    Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                }
                if ( clickDigitizerSelect==-1 ) {
                    Rectangle lrect= imageLocation;
                    if ( imageLocation==null ) return;
                    BufferedImage i = seq.currentImage().getImage();
                    if ( i==null ) return;
                    double factor = (double) lrect.getWidth() / (double) i.getWidth(null);
                    int imageX= (int)( ( e.getX() - lrect.x ) / factor );
                    int imageY= (int)( ( e.getY() - lrect.y ) / factor );                    
                    try {
                        clickDigitizer.doLookupMetadata( imageX, imageY, false );
                    } catch (IOException ex) {
                        Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (ParseException ex) {
                        Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                
            }

            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                if ( e.isPopupTrigger() ) {
                    getPopup().show(e.getComponent(),e.getX(), e.getY());
                }
                if ( clickDigitizerSelect==-1 ) {
                    Rectangle lrect= imageLocation;
                    if ( imageLocation==null ) return;
                    BufferedImage i = seq.currentImage().getImage();
                    if ( i==null ) return;
                    double factor = (double) lrect.getWidth() / (double) i.getWidth(null);
                    int imageX= (int)( ( e.getX() - lrect.x ) / factor );
                    int imageY= (int)( ( e.getY() - lrect.y ) / factor );                    
                    try {
                        clickDigitizer.doLookupMetadata( imageX, imageY, true );
                    } catch (IOException ex) {
                        Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                    } catch (ParseException ex) {
                        Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            
            @Override
            public void mouseClicked(MouseEvent e) { 
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                
            }
            
            
            
        };
        addMouseListener( ma );
        addMouseMotionListener( ma );

    }
    
    /**
     * return the position in the image's coordinates.
     * @param x the x location in the component.
     * @param y the y location in the component.
     * @return null or the Point location.
     */
    Point getImagePosition( int x, int y ) {
        Rectangle lrect= imageLocation;
        if ( imageLocation==null ) return null;
        BufferedImage i = seq.currentImage().getImage();
        if ( i==null ) return null;
        double factor = (double) lrect.getWidth() / (double) i.getWidth(null);

        int imageX= (int)( ( x - lrect.x ) / factor );
        int imageY= (int)( ( y - lrect.y ) / factor );        
        return new Point(imageX,imageY);
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

        if ( i!=null && clickDigitizer.viewer!=null && clickDigitizer.viewer.digitizer!=null ) {
            int h= i.getHeight();
            int w= i.getWidth();
            try {
                QDataSet ids= clickDigitizer.doTransform( );
                if ( ids!=null ) {
                    for ( int j=0; j<ids.length(); j++ ) {
                        QDataSet ids1= ids.rank()==2 ? ids.slice(j) : ids;
                        int ix= (int) ids1.value(0);
                        int iy= (int) ids1.value(1);
                        
                        if ( ix<0 || iy<0 ) continue;
                        if ( ix>=w || iy>=h ) continue;
                        
                        Rectangle lrect= imageLocation;
                        if ( imageLocation==null ) return;

                        double factor = (double) lrect.getWidth() / (double) w;

                        int imageX= (int)( ( ix * factor + lrect.x ) );
                        int imageY= (int)( ( iy * factor + lrect.y ) );
                        if ( clickDigitizer.viewer.annoTypeChar=='+' ) { 
                            g2.setColor( Color.LIGHT_GRAY );
                            g2.drawLine( 0,imageY,getWidth(),imageY );
                            g2.drawLine( imageX,0,imageX,getHeight() );
                            Color c0= g2.getColor();
                            Stroke stroke0= g2.getStroke();
                            g2.setColor( g2.getBackground() );
                            g2.setStroke( new BasicStroke( 1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1.0f, new float[] { 1.f,3.f }, 0.f ) );
                            g2.drawLine( 0,imageY,getWidth(),imageY );
                            g2.drawLine( imageX,0,imageX,getHeight() );
                            g2.setColor( Color.BLACK );
                            g2.drawLine( imageX-20,imageY,imageX+20,imageY );
                            g2.drawLine( imageX,imageY-20,imageX,imageY+20 );
                            g2.setStroke( stroke0 );
                            g2.setColor( c0 );
                        } else if ( clickDigitizer.viewer.annoTypeChar=='|' ) { 
                            g2.drawLine( imageX,0,imageX,getHeight() );                            
                            Color c0= g2.getColor();
                            Stroke stroke0= g2.getStroke();
                            g2.setColor( g2.getBackground() );
                            g2.setStroke( new BasicStroke( 1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1.0f, new float[] { 1.f,3.f }, 0.f ) );
                            g2.drawLine( imageX,0,imageX,getHeight() );
                            g2.setStroke( stroke0 );
                            g2.setColor( c0 );
                        } else if ( clickDigitizer.viewer.annoTypeChar=='.' ) {
                            g2.drawOval( imageX-2, imageY-2, 5, 5 );
                            Color c0= g2.getColor();
                            g2.setColor( g2.getBackground() );
                            g2.fillOval( imageX-1, imageY-1, 3, 3 );
                            g2.setColor( c0 );
                        }
                        
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}
