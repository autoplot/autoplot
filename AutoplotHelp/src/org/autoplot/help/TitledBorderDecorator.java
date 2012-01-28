/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.autoplot.help;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import javax.swing.event.MouseInputAdapter;

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

        final MouseInputAdapter ma= new MouseInputAdapter() {
            boolean within0= false;

            boolean isWithin( MouseEvent e ) {
                int y= e.getY() ;
                int x= e.getX() ;

                FontMetrics fm= e.getComponent().getGraphics().getFontMetrics();
                Rectangle2D bounds= fm.getStringBounds( tb.getTitle(),e.getComponent().getGraphics() );

                boolean within;
                if ( y>0 && y<16 && x>4 && x<(bounds.getWidth()+12) ) { // 4 and 8 by experiment on Linux.
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

            public void mouseExited(MouseEvent e) {
                tb.setTitleColor(c0);
                jPanel1.repaint();
                within0= false;
            }

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
