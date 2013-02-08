/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.virbo.autoplot;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.Color;
import java.awt.DisplayMode;
import java.awt.Event;
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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.virbo.autoplot.util.TickleTimer;

/**
 * Jeremy's experiment that will create automatic documentation.
 * @author jbf
 */
public class AutoScreenshotsTool extends EventQueue {

    public static void start( String outLocationFolder ) throws IOException {

        Toolkit.getDefaultToolkit().getSystemEventQueue().push(
                new AutoScreenshotsTool( outLocationFolder ));
    }

    public AutoScreenshotsTool( String outLocationFolder ) throws IOException {
        this.outLocationFolder= new File( outLocationFolder );
        boolean fail= false;
        if (!this.outLocationFolder.exists()) {
            fail= !this.outLocationFolder.mkdirs();
        }
        if ( fail ) throw new IOException("output folder cannot be created");
        logFile= new BufferedWriter( new FileWriter( new File( this.outLocationFolder, tp.format( TimeUtil.now(), null ) + ".txt" ) ) );

        tickleTimer= new TickleTimer( 200, new PropertyChangeListener() {
            public void propertyChange(PropertyChangeEvent evt) {
                long t1= System.currentTimeMillis();
                doit( t1, t1-tb, 99999 );
            }
        } );

    }


    long t0 = 0;
    long tb = System.currentTimeMillis();
    
    /*
     * block>0 means decrement.  block<0 means wait.
     */
    int block= 0;

    static BufferedImage pnt;
    static {
        try {
            pnt = ImageIO.read(AutoScreenshotsTool.class.getResource("/resources/pointer.png"));
        } catch (IOException ex) {
            Logger.getLogger(AutoScreenshotsTool.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    static int ptrXOffset= 7;
    static int ptrYOffset= 3;
    File outLocationFolder;
    BufferedWriter logFile;
    TimeParser tp = TimeParser.create("$Y$m$d_$H$M$S");
    TickleTimer tickleTimer;

    //mask out parts of the desktop that are not autoplot...
    static void filterBackground( Graphics2D g, Rectangle b ) {
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
    

    public static BufferedImage getScreenShot( ) {
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
            if ( MouseInfo.getPointerInfo().getDevice()==gs[i] ) {
                try {
                    screenshots[i] = new Robot(gs[i]).createScreenCapture(bounds);
                } catch (AWTException ex) {
                    Logger.getLogger(AutoScreenshotsTool.class.getName()).log(Level.SEVERE, null, ex);
                }
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

    private void doit( long t1, long dt, int id ) {
        t0= t1;

        long processTime0= System.currentTimeMillis();

        final File file = new File( outLocationFolder, tp.format(TimeUtil.now(), null) + "_" + String.format("%06d", dt/100 ) + "_" + String.format("%05d", id ) + ".png" );

        final BufferedImage im = getScreenShot( );
        System.err.println(""+file+" screenshot aquired in "+ ( System.currentTimeMillis() - processTime0 ) +"ms.");

        try {
            file.createNewFile();
            ImageIO.write(im, "png", file);
            long processTime= ( System.currentTimeMillis() - processTime0 );
            System.err.println(""+file+" created in "+processTime+"ms.");

        } catch ( Exception ex ) {
        }

        t0= System.currentTimeMillis();

    }

    @Override
    public void dispatchEvent(AWTEvent theEvent) {

        super.dispatchEvent(theEvent);

        long t1 = System.currentTimeMillis();
        long dt= t1 - tb;
        
        boolean reject= false;

        List keep= Arrays.asList( Event.MOUSE_DRAG, Event.MOUSE_MOVE, PaintEvent.PAINT, PaintEvent.UPDATE, 204, 205 ); 
        List skip= Arrays.asList( 1200 );

        if ( skip.contains( theEvent.getID() ) ) {
            reject= true;
            if ( theEvent.getID()==1200 ) {
                if ( this.peekEvent(1200)==null ) {
                    // we want to use this if it will cause repaint.
                    String ps= ((java.awt.event.InvocationEvent)theEvent).paramString();
                    if ( ps.contains("ComponentWorkRequest") ) { // nasty!
                        reject= false;
                    }
                } else {
                    System.err.println("other repaints are coming");
                }
            }
        } else {
            
        }

        reject= reject || ( (t1 - t0) < 200);

        try {
            logFile.write(String.format("%06d %1d %5d %s\n", dt / 100, reject ? 0 : 1, theEvent.getID(), theEvent.getClass().getName()));
            logFile.flush();
        } catch (IOException ex) {
            Logger.getLogger(AutoScreenshotsTool.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (  !reject ) {
            doit( t1, dt, theEvent.getID() );
        }

        if ( theEvent.getID()==Event.MOUSE_MOVE ) {
            tickleTimer.tickle( String.valueOf(theEvent.getID()) );
        }
    }
}
