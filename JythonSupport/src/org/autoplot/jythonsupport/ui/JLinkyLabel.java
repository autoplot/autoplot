
package org.autoplot.jythonsupport.ui;

import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JLabel;
import org.das2.util.LoggerManager;
import org.das2.util.filesystem.HtmlUtil;

/**
 * check for links in label.
 * @author jbf
 */
public final class JLinkyLabel extends JLabel {

    private final Logger logger= LoggerManager.getLogger("jython.editor");
    
    URI uri;
    
    public JLinkyLabel( URL context, String label ) {
        super(label);
        List<URL> uu= HtmlUtil.getLinks( context, label );
        if ( uu.size()>0 ) {
            try {
                uri= uu.get(0).toURI();
            } catch (URISyntaxException ex) {
                logger.log(Level.INFO, "Unable to use URL: {0}", uu.get(0));
                uri= null;
            }
        } else if ( uu.size()>1 ) {
            uri= null;
        }
        addMouseListener( myMouseLister() );
    }

    private MouseListener myMouseLister() {
        return new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    Desktop.getDesktop().browse( uri );
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, null, ex);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                JLinkyLabel.this.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                JLinkyLabel.this.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            }
            
        };
    }
    
}
