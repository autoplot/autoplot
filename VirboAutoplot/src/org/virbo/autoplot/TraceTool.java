/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;

/**
 * Jeremy's experiment that will create automatic documentation.
 * @author jbf
 */
public class TraceTool extends EventQueue {

    public static void start( File outLocationFolder ) {
        try {
            pnt = ImageIO.read( TraceTool.class.getResource("/resources/pointer.png"));
        } catch (IOException ex) {
            Logger.getLogger(TraceTool.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        Toolkit.getDefaultToolkit().getSystemEventQueue().push(
                new TraceTool( outLocationFolder ));
    }

    public TraceTool( File outLocationFolder ) {
        this.outLocationFolder= outLocationFolder;
    }


    long t0 = 0;

    static BufferedImage pnt;
    int ptrXOffset= 7;
    int ptrYOffset= 3;
    File outLocationFolder;

    BufferedImage getScreenShot( AWTEvent theEvent ) {
        Window w= ScriptContext.getViewWindow();

        final BufferedImage image = new BufferedImage( w.getWidth(), w.getHeight(), BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics;
        graphics = (Graphics2D) image.getGraphics();
        graphics.setColor( w.getBackground() );
        graphics.fillRect(0, 0, image.getWidth(w), image.getHeight(w));
        graphics.setColor( w.getForeground() );
        graphics.setBackground( w.getBackground() );
        w.print(graphics);

        //if ( theEvent instanceof MouseEvent ) {
            //MouseEvent me= ((MouseEvent)theEvent);
            PointerInfo info= MouseInfo.getPointerInfo();
            Point p= info.getLocation();

            graphics.drawImage( pnt, p.x - w.getX() - ptrXOffset, p.y - w.getY() - ptrYOffset, null );
            
        //}

        return image;
    }


    @Override
    public void postEvent(AWTEvent theEvent) {
//        System.err.println("theEvent: "+theEvent);
        long t1 = System.currentTimeMillis();

        if ((t1 - t0) > 1000) {
            t0 = t1;
            File f0 = new File("/tmp/ap/");
            if (!f0.exists()) {
                f0.mkdirs();
            }
            TimeParser tp = TimeParser.create("$Y$m$d_$H$M$S.png");
            f0 = new File( outLocationFolder, tp.format(TimeUtil.now(), null));
            final File file = f0;
            final BufferedImage im = getScreenShot( theEvent );
            Runnable run = new Runnable() {

                public void run() {
                    try {
                        file.createNewFile();
                        ScriptContext.getViewWindow();
                        ImageIO.write(im, "png", file);

                    } catch (IOException ex) {
                        Logger.getLogger(TraceTool.class.getName()).log(Level.SEVERE, null, ex);
                    }

                }
            };
            new Thread(run).start();

        }
        super.postEvent(theEvent);
    }
}
