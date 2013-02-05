/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.DisplayMode;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.PaintEvent;
import java.awt.geom.Area;
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
public class AutoScreenshotsTool extends EventQueue {

    public static void start( String outLocationFolder ) {
        try {
            pnt = ImageIO.read( AutoScreenshotsTool.class.getResource("/resources/pointer.png"));
        } catch (IOException ex) {
            Logger.getLogger(AutoScreenshotsTool.class.getName()).log(Level.SEVERE, null, ex);
            return;
        }

        Toolkit.getDefaultToolkit().getSystemEventQueue().push(
                new AutoScreenshotsTool( outLocationFolder ));
    }

    public AutoScreenshotsTool( String outLocationFolder ) {
        this.outLocationFolder= new File( outLocationFolder );
    }


    long t0 = 0;
    long tb = System.currentTimeMillis();
    
    /*
     * block>0 means decrement.  block<0 means wait.
     */
    int block= 0;

    static BufferedImage pnt;
    int ptrXOffset= 7;
    int ptrYOffset= 3;
    File outLocationFolder;

    //mask out parts of the desktop that are not autoplot...
    void filterBackground( Graphics2D g, Rectangle b ) {
        Color c= new Color( 255,255,255,255 );
        g.setColor(c);

        Rectangle r= new Rectangle(0,0,2000,2000);
        Area s= new Area(r);

        Frame[] frames = Frame.getFrames();
        for (Frame frame : frames) {
            if ( frame.isVisible() ) {
                Rectangle rect= frame.getBounds();
                rect.translate( -b.x, -b.y );
                s.subtract( new Area( rect ) );
            }
        }

        Window[] windows= Window.getWindows();
        for ( Window window: windows ) {
            if ( window.isVisible() ) {
                Rectangle rect= window.getBounds();
                rect.translate( -b.x, -b.y );
                s.subtract( new Area( rect ) );
            }
        }

        g.fill(s);
        
    }

    BufferedImage getScreenShot( ) {
        //http://www.javalobby.org/forums/thread.jspa?threadID=16400&tstart=0
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        BufferedImage[] screenshots = new BufferedImage[gs.length];

        DisplayMode mode;
        Rectangle bounds;

        int active=-1;
        for(int i=0; i<gs.length; i++) {
            mode = gs[i].getDisplayMode();
            bounds = new Rectangle(0, 0, mode.getWidth(), mode.getHeight());
            try {
                screenshots[i] = new Robot(gs[i]).createScreenCapture(bounds);
            } catch (AWTException ex) {
                Logger.getLogger(AutoScreenshotsTool.class.getName()).log(Level.SEVERE, null, ex);
            }
            if ( MouseInfo.getPointerInfo().getDevice()==gs[i] ) {
                PointerInfo info= MouseInfo.getPointerInfo();
                Point p= info.getLocation();
                Rectangle b= info.getDevice().getDefaultConfiguration().getBounds();
                active= i;
                screenshots[i].getGraphics().drawImage( pnt, p.x - b.x - ptrXOffset, p.y - b.y - ptrYOffset, null );
                filterBackground( (Graphics2D)screenshots[i].getGraphics(), b );
            }
        }

        return screenshots[active];
    }


    @Override
    public void dispatchEvent(AWTEvent theEvent) {

        long t1 = System.currentTimeMillis();
        long dt= t1 - tb;
        
        String.format("%06d %5d %s\n", dt/100, theEvent.getID(), theEvent.getClass().getName() );

        boolean reject= false;

        //System.err.println( "event id= "+theEvent.getID() );

        if ( !( theEvent.getID()==PaintEvent.PAINT ) ) {
            reject= false;
        } else {
            
        }

        super.dispatchEvent(theEvent);

        if (  !reject && ( (t1 - t0) > 200) ) {
            t0= t1;
            
            boolean fail= false;
            if (!outLocationFolder.exists()) {
                fail= !outLocationFolder.mkdirs();
            }

            if ( !fail ) {
                long processTime0= System.currentTimeMillis();

                TimeParser tp = TimeParser.create("$Y$m$d_$H$M$S");
               
                final File file = new File( outLocationFolder, tp.format(TimeUtil.now(), null) + "_" + String.format("%06d", dt/100 ) + ".png" );

                final BufferedImage im = getScreenShot( );

                try {
                    file.createNewFile();
                    ImageIO.write(im, "png", file);
                    long processTime= ( System.currentTimeMillis() - processTime0 );

                } catch ( Exception ex ) {
                }

            }
            t0= System.currentTimeMillis();

            //new Thread(run).start();
        }
    }
}
