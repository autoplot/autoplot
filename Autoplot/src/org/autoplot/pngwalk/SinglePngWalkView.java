package org.autoplot.pngwalk;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.text.ParseException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.AbstractAction;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.das2.qds.QDataSet;

/**
 * An implementation of PngWalkView to display a single image.
 * @author Ed Jackson
 */
public final class SinglePngWalkView extends PngWalkView {

//    private boolean sizeValid = false;
    private transient BufferedImage cacheImage;

    Rectangle imageLocation= null;
    
    AffineTransform affineTransform= new AffineTransform();
    MouseWheelListener delegate= getMouseWheelListener();
    Point mousePressPoint= null;
    
    transient ClickDigitizer clickDigitizer;
    int clickDigitizerSelect= -1;
    
    private PngWalkTool viewer=null;
    
    public SinglePngWalkView(WalkImageSequence s) {
        this( s, null );
    }    
    
    public SinglePngWalkView(WalkImageSequence s, PngWalkTool viewer ) {
        super(s);
        clickDigitizer= new ClickDigitizer( this );
        
        setShowCaptions(true);
        
        addMouseWheelListener( new MouseWheelListener() {
            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                if ( ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK )==KeyEvent.CTRL_DOWN_MASK ) {
                    affineTransform.scale( 1-(.04*e.getWheelRotation()), 1-(.04*e.getWheelRotation()) );
                    repaint();
                } else {
                    delegate.mouseWheelMoved(e);
                }
            }
        });
        
        MouseAdapter ma= new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if ( e.isPopupTrigger() ) {
                    JPopupMenu m= getPopup();
                    m.add( new JMenuItem( new AbstractAction( "Reset Zoom (Ctrl+MouseWheel to set)" ) {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            affineTransform= new AffineTransform();
                            repaint();
                        }
                    } ) );
                    m.show(e.getComponent(),e.getX(), e.getY());
                    return;
                }
                mousePressPoint= e.getPoint();
                Point p= getImagePosition( e.getX(), e.getY() );
                
                MouseAdapter ma= viewer!=null ? viewer.getImageMouseAdapter() : null;
                if ( ma!=null ) {
                    String img= seq.getSelectedName();
                    MouseEvent ep= new MouseEvent( e.getComponent(), e.getID(), e.getWhen(), e.getModifiers(), p.x, p.y, e.getClickCount(), e.isPopupTrigger(), e.getButton() );
                    ep.setSource(img);
                    ma.mousePressed( ep );
                    return;
                }
                if ( ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK ) == KeyEvent.CTRL_DOWN_MASK ) {
                    return;
                }
                if ( e.getButton()!=MouseEvent.BUTTON1 ) {
                    return;
                }
                if ( p!=null ) try {
                    clickDigitizerSelect= clickDigitizer.maybeSelect(p);
                } catch (IOException | ParseException ex) {
                    Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                }
                if ( clickDigitizerSelect==-1 ) {
                    Rectangle lrect= imageLocation;
                    if ( imageLocation==null ) return;
                    Point2D clickPos= new Point2D.Double(e.getX(),e.getY());
                    if ( !affineTransform.isIdentity() ) {
                        try {
                            clickPos= affineTransform.inverseTransform(clickPos,null);
                        } catch (NoninvertibleTransformException ex) {
                            Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    BufferedImage i = seq.currentImage().getImage();
                    if ( i==null ) return;
                    double factor = (double) lrect.getWidth() / (double) i.getWidth(null);
                    int imageX= (int)( ( clickPos.getX() - lrect.x ) / factor );
                    int imageY= (int)( ( clickPos.getY() - lrect.y ) / factor );                    
                    try {
                        clickDigitizer.doLookupMetadata( imageX, imageY, false );
                    } catch (IOException | ParseException ex) {
                        Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
                if ( viewer!=null && p!=null ) {
                    viewer.firePropertyChange( PngWalkTool.PROP_MOUSEPRESSLOCATION, null, p );
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                super.mouseReleased(e);
                if ( e.isPopupTrigger() ) {
                    JPopupMenu m= getPopup();
                    m.add( new JMenuItem( new AbstractAction( "Reset Zoom (Ctrl+MouseWheel to set)" ) {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            affineTransform= new AffineTransform();
                            repaint();
                        }
                    } ) );
                    m.show(e.getComponent(),e.getX(), e.getY());
                    return;
                }
                
                Point p= getImagePosition( e.getX(), e.getY() );
                MouseAdapter ma= viewer!=null ? viewer.getImageMouseAdapter() : null;
                if ( ma!=null ) {
                    String img= seq.getSelectedName();
                    MouseEvent ep= new MouseEvent( e.getComponent(), e.getID(), e.getWhen(), e.getModifiers(), p.x, p.y, e.getClickCount(), e.isPopupTrigger(), e.getButton() );
                    ep.setSource(img);
                    ma.mouseReleased( ep );
                    return;
                }
                
                if ( e.getButton()!=MouseEvent.BUTTON1 ) {
                    return;
                }                
                if ( clickDigitizerSelect==-1 ) {
                    Rectangle lrect= imageLocation;
                    if ( imageLocation==null ) return;
                    
                    Point2D clickPos= new Point2D.Double(e.getX(),e.getY());
                    if ( !affineTransform.isIdentity() ) {
                        try {
                            clickPos= affineTransform.inverseTransform(clickPos,null);
                        } catch (NoninvertibleTransformException ex) {
                            Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    }
                    BufferedImage i = seq.currentImage().getImage();
                    if ( i==null ) return;
                    double factor = (double) lrect.getWidth() / (double) i.getWidth(null);
                    int imageX= (int)( ( clickPos.getX() - lrect.x ) / factor );
                    int imageY= (int)( ( clickPos.getY() - lrect.y ) / factor );                    
                    
                    try {
                        clickDigitizer.doLookupMetadata( imageX, imageY, true );
                    } catch (IOException | ParseException ex) {
                        Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            
            @Override
            public void mouseClicked(MouseEvent e) { 
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if ( mousePressPoint!=null ) {
                    if ( ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK ) == KeyEvent.CTRL_DOWN_MASK ) {
                        Point p= e.getPoint();
                        affineTransform.translate( (p.x-mousePressPoint.x) / affineTransform.getScaleX(),
                            ( p.y-mousePressPoint.y ) / affineTransform.getScaleY() );
                        mousePressPoint= p;
                        repaint();
                    }
                }
                Point p= getImagePosition( e.getX(), e.getY() );
                MouseAdapter ma= viewer!=null ? viewer.getImageMouseAdapter() : null;
                if ( ma!=null ) {
                    String img= seq.getSelectedName();
                    MouseEvent ep= new MouseEvent( e.getComponent(), e.getID(), e.getWhen(), e.getModifiers(), p.x, p.y, e.getClickCount(), e.isPopupTrigger(), e.getButton() );
                    ep.setSource(img);
                    ma.mouseDragged(ep );
                }
            }
            
        };
        addMouseListener( ma );
        addMouseMotionListener( ma );
        this.setPreferredSize( new Dimension(300,300) );  
        
        setViewer( viewer );
    }
    
    /**
     * set the pngwalkTool using this view.
     * @param viewer 
     */
    protected void setViewer( PngWalkTool viewer ) {
        this.viewer= viewer;
        this.clickDigitizer.setViewer(viewer);
    }
       
    /**
     * return the position in the image's coordinates.
     * @param x the x location in the component.
     * @param y the y location in the component.
     * @return null or the Point location.
     */
    private Point getImagePosition( int x, int y ) {
        Rectangle lrect= imageLocation;
        if ( imageLocation==null ) return null;
        BufferedImage i = seq.currentImage().getImage();
        if ( i==null ) return null;
        double factor = (double) lrect.getWidth() / (double) i.getWidth(null);

        int imageX= (int)( ( x - lrect.x ) / factor );
        int imageY= (int)( ( y - lrect.y ) / factor );     
        return new Point(imageX,imageY);
    }
    
//    /**
//     * this was introduced to support digitizing points while zoomed into an
//     * image, but I am missing a transform somewhere and the feature will be
//     * disabled so I can make a release. 
//     * @param image
//     * @return 
//     */
//    private Rectangle transformRect( Rectangle image ) {
//        Rectangle result= new Rectangle();        
//        try {
//            Point p1= new Point(image.getLocation());
//            Point p2= new Point(image.getLocation().x+image.width,image.getLocation().y+image.height);
//            Point p3= new Point(image.getLocation());
//            Point p4= new Point(image.getLocation());
//            affineTransform.inverseTransform( p1, p3 );
//            affineTransform.inverseTransform( p2, p4 );
//            result= new Rectangle( p3, new Dimension( p4.x-p3.x, p4.y-p3.y ) );
//            return result;
//        } catch (NoninvertibleTransformException ex) {
//            throw new RuntimeException(ex);
//        }
//        
//    }
    
    @Override
    protected void paintComponent(Graphics g1) {
        super.paintComponent(g1);
        Graphics2D g2 = (Graphics2D) g1;
        
        AffineTransform at= g2.getTransform();
        at.concatenate(affineTransform);
        g2.setTransform(at);
        
        g2.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON );
        g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

        if (seq == null || seq.size()==0) return;

        BufferedImage i = seq.currentImage().getImage();
        
        long ageMillis= System.currentTimeMillis()-seq.currentImage().getInitLoadBirthTime();
        
        if (i!=null && i.getWidth(this) >0 && i.getHeight(this) > 0) {
            imageLocation= paintImageCentered(i, g2, seq.currentImage().getCaption());
            cacheImage = i;
        } else {
            if (cacheImage != null) {
                imageLocation= paintImageCentered(cacheImage, g2, seq.currentImage().getCaption());
            }
            if ( ageMillis > 100 ) {
                paintImageCentered(loadingImage, g2);
            } else {
                this.repaint(150);
            }
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
                        switch (clickDigitizer.viewer.annoTypeChar) {
                            case '+':
                                {
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
                                    break;
                                }
                            case '|':
                                {
                                    g2.drawLine( imageX,0,imageX,getHeight() );
                                    Color c0= g2.getColor();
                                    Stroke stroke0= g2.getStroke();
                                    g2.setColor( g2.getBackground() );
                                    g2.setStroke( new BasicStroke( 1, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 1.0f, new float[] { 1.f,3.f }, 0.f ) );
                                    g2.drawLine( imageX,0,imageX,getHeight() );
                                    g2.setStroke( stroke0 );
                                    g2.setColor( c0 );
                                    break;
                                }
                            case '.':
                                {
                                    g2.drawOval( imageX-2, imageY-2, 5, 5 );
                                    Color c0= g2.getColor();
                                    g2.setColor( g2.getBackground() );
                                    g2.fillOval( imageX-1, imageY-1, 3, 3 );
                                    g2.setColor( c0 );
                                    break;
                                }
                            default:
                                break;
                        }
                        
                    }
                }
            } catch (IOException ex) {
                Logger.getLogger(SinglePngWalkView.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        if ( viewer!=null && i!=null ) {
                Rectangle lrect= imageLocation;
                if ( imageLocation==null ) return;
                int w= i.getWidth();
                double factor = (double) lrect.getWidth() / (double) w;
                AffineTransform at1= AffineTransform.getTranslateInstance( lrect.x, lrect.y );
                at1.scale(factor, factor);
                g2.transform(at1);
                viewer.decorators.forEach((p) -> {
                    p.paint(g2);
                });

        }
        
    }
}
