
package org.autoplot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

/**
 * Component for showing the status of the data set selector / time range selector GUI.
 * @author jbf
 */
public final class UriTimeRangeToggleButton extends JComponent {

    public UriTimeRangeToggleButton( ) {
        try {
            img= ImageIO.read( UriTimeRangeToggleButton.class.getResource("/resources/tinyButton2.png") );
            imgUp=  ImageIO.read( UriTimeRangeToggleButton.class.getResource("/resources/tinyButtonUp.png") );
            imgDn=  ImageIO.read( UriTimeRangeToggleButton.class.getResource("/resources/tinyButtonDn.png") );
            setPosition(0);
        } catch (IOException ex) {
            Logger.getLogger(UriTimeRangeToggleButton.class.getName()).log(Level.SEVERE, null, ex);
        }
        setPreferredSize( new Dimension( img.getHeight(), img.getWidth() ) );
        setMaximumSize( new Dimension( img.getHeight(), img.getWidth() ) );
        setMinimumSize( new Dimension( img.getHeight(), img.getWidth() ) );

        this.setToolTipText("Green Data Set Selector (Ctrl-D), Blue for Time Range Selector (Ctrl-T)");
        
        MouseAdapter ma= getMouseAdapter();
        this.addMouseListener( ma );
        this.addMouseMotionListener( ma );
        
    }
           
    public static final String PROP_POSITION= "position";
    
    BufferedImage img;
    BufferedImage imgUp; // upper button is selected
    BufferedImage imgDn; // lower button is selected

    int pendingPos= -1;
    
    /**
     * 0 is up 1 is down.
     * @param pos 
     */
    public final void setPosition( int pos ) {
        if ( pos==1 ) {
            img= imgUp;
        } else {
            img= imgDn;
        }
        repaint();
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        g.setColor( this.getBackground() );
        //g.setColor( Color.BLUE );
        g.fillRect( 0, 0, img.getWidth()+2, img.getHeight()+2 );
        g.drawImage( img, 0, 0, this );
        if ( pendingPos>-1 ) {
            int y;
            if ( pendingPos==1 ) {
                y= img.getHeight()/2 * (1-pendingPos) + 4;
            } else {
                y= img.getHeight()/2 * (1-pendingPos) + ( pendingPos ) * 2;
            }
            g.setColor(Color.BLACK);
            g.drawRect( 1, y+1, img.getWidth()-2, img.getWidth()-2 );
        }
    }
    
    private MouseAdapter getMouseAdapter() {
        return new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if ( evt.getY()< img.getHeight() / 2 ) {
                    firePropertyChange( PROP_POSITION, -1, 1 );
                } else {
                    firePropertyChange( PROP_POSITION, -1, 0 );
                }
            }

            @Override
            public void mouseMoved(MouseEvent evt) {
                if ( evt.getY()< img.getHeight() / 2 ) {
                    pendingPos= 1;
                } else {
                    pendingPos= 0;
                }  
                repaint();
            }

            @Override
            public void mouseEntered(MouseEvent evt) {
                if ( evt.getY()< img.getHeight() / 2 ) {
                    pendingPos= 1;
                } else {
                    pendingPos= 0;
                }
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent evt) {
                pendingPos= -1;
                repaint();
            }
            
        };
    }
}
