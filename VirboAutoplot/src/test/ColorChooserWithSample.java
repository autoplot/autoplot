
package test;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.colorchooser.AbstractColorChooserPanel;

/**
 *
 * @author jbf
 */
public class ColorChooserWithSample {
    public static void main( String[] args ) {
        JColorChooser custom = new JColorChooser();
        custom.addChooserPanel( new AbstractColorChooserPanel() {

            @Override
            public void updateChooser() {
                
            }

            @Override
            protected void buildChooser() {
                this.setLayout( new BorderLayout() );
                final JLabel l = new JLabel("Grab colors from the desktop.");
                add( l, BorderLayout.NORTH );
                JButton b= new JButton("Pick From Desktop");
                final JLabel p= new JLabel("");
                add( p, BorderLayout.CENTER );
                b.addActionListener( new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        Robot r;
                        try {
                            l.setText("Getting Screen...");
                            r = new Robot();
                            BufferedImage im= r.createScreenCapture( new Rectangle(0,0,100,100) );
                            p.setIcon( new ImageIcon(im) );
                            l.setText("Got screen...");
                        } catch (AWTException ex) {
                            Logger.getLogger(ColorChooserWithSample.class.getName()).log(Level.SEVERE, null, ex);
                        }   
                    }
                });
                add( b, BorderLayout.SOUTH );
                
            }

            @Override
            public String getDisplayName() {
                return "Pixel Grabber";
            }

            @Override
            public Icon getSmallDisplayIcon() {
                return null;
            }

            @Override
            public Icon getLargeDisplayIcon() {
                return null;
            }
        }) ;
        if ( JOptionPane.OK_OPTION==JOptionPane.showConfirmDialog( null, custom ) ) {
            System.err.println("c: "+custom.getColor() );
        }
        
    }
}
