/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.virbo.autoplot;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 * Experimental component for showing the status of the data set selector.
 * @author jbf
 */
public class UriTimeRangeToggleButton extends JPanel {

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
        this.setToolTipText("Green Data Set Selector, Blue for Time Range Selector");
    }
    
    BufferedImage img;
    BufferedImage imgUp; // upper button is selected
    BufferedImage imgDn; // lower button is selected

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
        g.fillRect( 0, 0, img.getWidth()+1, img.getHeight()+1 );
        g.drawImage( img, 0, 0, this );
    }
    
}
