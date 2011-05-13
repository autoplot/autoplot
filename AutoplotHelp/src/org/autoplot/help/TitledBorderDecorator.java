/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.help;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/**
 * Makes titled border into link to documentation
 * @author jbf
 */
public class TitledBorderDecorator {
    
    public static void makeLink( final JPanel jPanel1, final ActionListener al ) {

        Border b= jPanel1.getBorder();
        if ( !( b instanceof TitledBorder ) ) {
            throw new IllegalArgumentException("JPanel must have titled border");
        }
        final TitledBorder tb= (TitledBorder) jPanel1.getBorder();
        final Color c0= tb.getTitleColor();

        MouseAdapter ma= new MouseAdapter() {
            boolean within0= false;

            boolean isWithin( MouseEvent e ) {
                int y= e.getY() ;
                int x= e.getX() ;

                boolean within;
                if ( y>0 && y<16 && x>4 && x<jPanel1.getWidth() ) {
                    within= true;
                } else {
                    within= false;
                }
                return within;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if ( isWithin(e) ) {
                    al.actionPerformed( new ActionEvent( jPanel1, 1, "help") );
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean within= isWithin(e);
                if ( within!=within0 ) {
                    if ( within ){
                        tb.setTitleColor(Color.BLUE);
                    } else {
                        tb.setTitleColor(c0);
                    }
                    jPanel1.repaint();
                    within0= within;
                }
            }

        };

        jPanel1.addMouseListener( ma );
        jPanel1.addMouseMotionListener( ma );


    }
}
