
package org.virbo.autoplot;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Event;
import java.awt.EventQueue;
import java.awt.FlowLayout;
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
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
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
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.autoplot.pngwalk.PngWalkTool;
import org.das2.components.DasProgressPanel;
import org.das2.datum.LoggerManager;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.util.FileUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.virbo.autoplot.util.TickleTimer;

/**
 * Jeremy's experiment that will create automatic documentation.
 * This is intended to provide a means for users to more easily communicate and 
 * to make it easier to create documentation.
 * 
 * This is being modified a bit, namely to delay work such as screening
 * private regions, to improve responsiveness and to allow the user the option
 * of screening or not.
 * 
 * @author jbf
 */
public class ScreenshotsTool extends EventQueue {

    private static final int MOUSE_WHEEL_UP= 1;
    private static final int MOUSE_WHEEL_DOWN= 2;

    private static final Logger logger= LoggerManager.getLogger("autoplot.screenshots");
    
    /**
     * start should be called from the event thread.
     * @param parent the device
     */
    public static void start( Window parent ) {

        Preferences prefs= Preferences.userNodeForPackage( ScreenshotsTool.class );
        String s= prefs.get( "outputFolder", System.getProperty("user.home") );

        JPanel p= new JPanel();
        p.setLayout( new BorderLayout() );

        p.add( new JLabel( "<html>This will automatically take screenshots, recording them to a folder.<br>The folder must be empty.<br>Hold Ctrl and press Shift twice to stop recording." ), BorderLayout.CENTER );

        JPanel folderPanel= new JPanel();
        folderPanel.setLayout( new FlowLayout() );
        folderPanel.add( new JLabel( "Output Folder:" ) );
        final JTextField tf= new JTextField(20);
        tf.setText(s);
        folderPanel.add( tf );

        folderPanel.add( new JButton( new AbstractAction( "Pick", new ImageIcon( ScreenshotsTool.class.getResource("/org/virbo/autoplot/file.png") ) ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser ch= new JFileChooser();
                ch.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
                ch.setCurrentDirectory( new File( tf.getText() ).getParentFile() );
                ch.setSelectedFile( new File( tf.getText() ) );
                if ( ch.showOpenDialog(tf)==JFileChooser.APPROVE_OPTION ) {
                    tf.setText(ch.getSelectedFile().toString());
                }
            }
        }));
        p.add( folderPanel, BorderLayout.SOUTH );

        int r= JOptionPane.showConfirmDialog( parent, p,
        "Record Screenshots",
        JOptionPane.OK_CANCEL_OPTION );

        if ( r==JOptionPane.OK_OPTION ) {
            File f= new File( tf.getText() );
            if ( f.exists() && f.listFiles().length>1 ) {
                if ( JOptionPane.OK_OPTION==AutoplotUtil.showConfirmDialog( parent,"Folder is not empty.  Delete contents before starting?", "Folder must be empty", JOptionPane.OK_CANCEL_OPTION ) ) {
                    if ( !FileUtil.deleteFileTree(f) ) {
                        JOptionPane.showMessageDialog(parent,"Unable to delete files");
                    }
                } else {
                    return;
                }
            }
            try {
                prefs.put( "outputFolder", tf.getText() );
                try{
                    prefs.flush();
                } catch ( BackingStoreException e ) {}
                Toolkit.getDefaultToolkit().getSystemEventQueue().push(
                    new ScreenshotsTool( parent, tf.getText() ));
            } catch ( IOException ex ) {
                throw new RuntimeException(ex);
            }
        }
    }

    public ScreenshotsTool( Window parent, String outLocationFolder ) throws IOException {
        this.outLocationFolder= new File( outLocationFolder );
        boolean fail= false;
        if (!this.outLocationFolder.exists()) {
            fail= !this.outLocationFolder.mkdirs();
        }
        if ( fail ) throw new IOException("output folder cannot be created");
        if ( this.outLocationFolder.listFiles().length>1 ) {
            throw new IOException("output folder must be empty");
        }
        logFile= new BufferedWriter( new FileWriter( new File( this.outLocationFolder, tp.format( TimeUtil.now(), null ) + ".txt" ) ) );

        this.parent= parent;
        active= getActiveDisplay( parent );
        bounds= null;

        tickleTimer= new TickleTimer( 300, new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                AWTEvent update= peekEvent(1200);
                if ( update==null ) {
                    long t1= System.currentTimeMillis();
                    Rectangle r= doTakePicture( t1, t1-tb, 99999 );
                    if ( bounds==null ) bounds= r; else bounds= bounds.union(r);

                } else {
                    if ( canReject(update) ) {
                        long t1= System.currentTimeMillis();
                        Rectangle r= doTakePicture( t1, t1-tb, 99999 );
                        if ( bounds==null ) bounds= r; else bounds= bounds.union(r);
                    }
                    //System.err.println("update coming anyway");
                }
            }
        } );

    }

    /**
     * return the display that the window is within.  For single-head machines, this is 0.
     * @param parent
     * @return
     */
    private static int getActiveDisplay( Window parent ) {
        int active= -1;
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

        GraphicsDevice[] gs = ge.getScreenDevices();
        GraphicsDevice target= parent.getGraphicsConfiguration().getDevice();

        for(int i=0; i<gs.length; i++) {
            if ( gs[i]==target ) active= i;
        }
        logger.log(Level.FINE, "active display is #{0}", active);  //TODO: track this down where the wrong display is used when mouse pointer is on the other screen.
        return active;
    }

    /**
     * return the bounds of the active display.
     * @param active
     * @return
     */
    private static Rectangle getScreenBounds( int active ) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();

        int i = active;
        Rectangle b= gs[i].getDefaultConfiguration().getBounds();
        
        return b;
    }

    long t0 = 0;
    long tb = System.currentTimeMillis();
    
    /*
     * block>0 means decrement.  block<0 means wait.
     */
    int block= 0;

    static BufferedImage pnt;
    static BufferedImage pnt_b1;
    static BufferedImage pnt_b2;
    static BufferedImage pnt_b3;
    static BufferedImage pnt_w1;
    static BufferedImage pnt_w2;

    /**
     * mouse buttons
     */
    static int button=0;

    static {
        try {
            pnt = ImageIO.read(ScreenshotsTool.class.getResource("/resources/pointer.png"));
            pnt_b1 = ImageIO.read(ScreenshotsTool.class.getResource("/resources/pointer_b1.png"));
            pnt_b2 = ImageIO.read(ScreenshotsTool.class.getResource("/resources/pointer_b2.png"));
            pnt_b3 = ImageIO.read(ScreenshotsTool.class.getResource("/resources/pointer_b3.png"));
            pnt_w1 = ImageIO.read(ScreenshotsTool.class.getResource("/resources/pointer_w1.png"));
            pnt_w2 = ImageIO.read(ScreenshotsTool.class.getResource("/resources/pointer_w2.png"));
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }
    }
    static int ptrXOffset= 7;
    static int ptrYOffset= 3;
    File outLocationFolder;
    BufferedWriter logFile;
    TimeParser tp = TimeParser.create("$Y$m$d_$H$M$S");
    TickleTimer tickleTimer;

    /**
     * calculate the bounds we are going to keep, which will be typically by 
     * significantly smaller than the display.
     * @param b the bounds concerning this process.
     * @return the bounds relative to the input rectangle b.
     */
    static Rectangle getMyBounds( Rectangle b ) {
        Rectangle r= null;

        Frame[] frames = Frame.getFrames();
        for (Frame frame : frames) {
            if ( frame.isVisible() ) {
                Rectangle rect= frame.getBounds();
                if ( r==null ) r= rect; else r= r.union(rect);
            }
        }

        Window[] windows= Window.getWindows();
        for ( Window window: windows ) {
            if ( window.isVisible() ) {
                Rectangle rect= window.getBounds();
                if ( r==null ) r= rect; else r= r.union(rect);
            }
        }

        if ( r==null ) {
            throw new IllegalArgumentException("unable to find rectangle");
        }
        r= r.intersection(b);
        r.width= Math.max(0,r.width);
        r.height= Math.max(0,r.height);
        r.translate( -b.x, -b.y );
        return r;

    }

    /**
     * mask out parts of the desktop that are not Autoplot, for the user's privacy.
     * It's been shown that this takes just a few milliseconds.
     * @param g the graphics to paint on.
     * @param b the rectangle showing the display translation.
     * @return true if the mouse pointer is within a rectangle boundary.
     */
    private static boolean filterBackground( Graphics2D g, Rectangle b, Point p ) {
        
        long t0= System.currentTimeMillis();
        
        Color c= new Color( 255,255,255,255 );
        g.setColor(c);

        Rectangle r= g.getDeviceConfiguration().getBounds();
        r= new Rectangle(0,0,r.width,r.height);

        Area s= new Area(r);

        boolean containsPointer= false;
        
        Frame[] frames = Frame.getFrames();
        for (Frame frame : frames) {
            if ( frame.isVisible() ) {
                if( frame.getExtendedState() != Frame.ICONIFIED ) {
                    Rectangle rect= frame.getBounds();
                    if ( rect.contains(p) ) containsPointer=true;
                    rect.translate( -b.x, -b.y );
                    s.subtract( new Area( rect ) );
                    
                }
            }
        }

        Window[] windows= Window.getWindows();
        for ( Window window: windows ) {
            if ( window.isVisible() ) {
                if ( window.isShowing() ) {
                    Rectangle rect= window.getBounds();
                    if ( rect.contains(p) ) containsPointer=true;
                    rect.translate( -b.x, -b.y );
                    s.subtract( new Area( rect ) );
                }
            }
        }

        g.fill(s);
        
        logger.log(Level.FINE, "filterBackground in {0}ms", (System.currentTimeMillis()-t0));
        return containsPointer;
        
    }

    /**
     * return the common bounding rectangle to all png images in the directory.  
     * @param root folder containing png images.
     * @param monitor progress monitor for the task
     * @return the rectangle common to all images.
     * @throws IOException
     * @see #getTrim(java.awt.image.BufferedImage) 
     */
    public static Rectangle getTrim( File root, ProgressMonitor monitor ) throws IOException {
        if ( !root.canRead() ) throw new IllegalArgumentException("cannot read root: "+root );
        if ( !root.isDirectory() ) throw new IllegalArgumentException("root should be directory: " +root );
        File[] ff= root.listFiles();
        Rectangle result= null;
        int c= ff.length;
        int i= 1;
        monitor.setTaskSize(c);
        monitor.setProgressMessage( "find bounds for set");
        for ( File f : ff ) {
            monitor.setTaskProgress( i );
            if ( f.toString().endsWith(".png") ) {
                BufferedImage im= ImageIO.read(f);
                Rectangle r1= getTrim( im );
                if ( result==null ) {
                    result= r1;
                } else {
                    result= result.union(r1);
                }
            }
            i++;
        }
        return result;
    }
    
    /**
     * return the rectangle containing the image.  The background is determined by looking at the upper-left 
     * pixel, and the rectangle bounding the non-background pixels is returned.
     * Thanks to http://stackoverflow.com/questions/10678015/how-to-auto-crop-an-image-white-border-in-java
     * @param source the image, containing a base color in the upper right corner.
     * @return the rectangle tightly containing the windows.
     */
    public static Rectangle getTrim( BufferedImage source ) {
        int baseColor = source.getRGB(0, 0);

        int width = source.getWidth();
        int height = source.getHeight();

        int topY = Integer.MAX_VALUE, topX = Integer.MAX_VALUE;
        int bottomY = -1, bottomX = -1;
        for(int y=0; y<height; y++) {
            for(int x=0; x<width; x++) {
                if ( baseColor != source.getRGB(x, y) ) {
                    if (x < topX) topX = x;
                    if (y < topY) topY = y;
                    if (x > bottomX) bottomX = x;
                    if (y > bottomY) bottomY = y;
                }
            }
        }
        bottomX= bottomX+1;
        bottomY= bottomY+1;
        return new Rectangle( topX, topY, bottomX-topX, bottomY-topY );
    }


    /**
     * trim off the excess white to make a smaller image
     * @param image
     * @return
     */
    public static BufferedImage trim( BufferedImage image ) {
        Rectangle r= getTrim(image);
        return trim( image, r );
    }

    /**
     * trim off the excess white to make a smaller image
     * @param image the image
     * @param r the rectangle
     * @return the smaller image
     */
    public static BufferedImage trim( BufferedImage image, Rectangle r ) {
        return image.getSubimage( r.x, r.y, r.width, r.height );
    }

    /**
     * find the common trim bounding box and trim all the images in the directory.
     * @param dir
     * @throws IOException
     */
    public static void trimAll( File dir ) throws IOException {
        trimAll( dir, null, new NullProgressMonitor() );
    }

    /**
     * find the common trim bounding box and trim all the images in the directory.
     * @param dir folder containing the images.
     * @param r the bounding rectangle, or null if getTrim should be used.
     * @param monitor
     * @throws IOException
     */
    public static void trimAll( File dir, Rectangle r, ProgressMonitor monitor ) throws IOException {

        if ( !dir.exists() ) throw new IllegalArgumentException("directory does not exist: "+dir );
        if ( !dir.canRead() ) throw new IllegalArgumentException("directory cannot be read: "+dir );
        File[] ff= dir.listFiles();

        monitor.started();

        if ( r==null ) r= getTrim( dir, monitor );
        monitor.setProgressMessage("trim images");
        monitor.setTaskSize( ff.length );
        int i=0;
        for ( File f : ff ) {
            i++;
            monitor.setTaskProgress( i );
            if ( f.toString().endsWith(".png") ) {
                BufferedImage im= ImageIO.read(f);
                im= trim( im, r );
                ImageIO.write( im, "png", f );
            }
        }

        monitor.finished();
        
    }
    
    /**
     * get a screenshot of the display Autoplot's main UI is running within.
     * @return
     */
    public static BufferedImage getScreenShot() {
        Window w= ScriptContext.getViewWindow();
        int active= getActiveDisplay(w);
        return getScreenShot(active,0,true);
    }

    /** 
     * get a screenshot of the display Autoplot's main UI is running within, but without the pointer.
     * @return
     */
    public static BufferedImage getScreenShotNoPointer(  ) {
        Window w= ScriptContext.getViewWindow();
        int active= getActiveDisplay(w);
        return getScreenShot(active,0,false);        
    }
    
    /**
     * Get a screenshot of the display indicated by active.  Only one screen
     * of a dual-head is returned.
     * @param active the display number.  See getActiveDisplay(window);
     * @return image of the screen.
     */
    public static BufferedImage getScreenShot( int active ) {
        return getScreenShot( active, 0, true );
    }
 
    /**
     * Get a screenshot of the display indicated by active.  Only one screen
     * of a dual-head is returned.  The buttons integer indicates that button presses
     * or wheel events should be indicated.
     * @param active the display number.  See getActiveDisplay(window);
     * @param buttons one of: MouseEvent.BUTTON1_DOWN_MASK, MouseEvent.BUTTON2_DOWN_MASK, MouseEvent.BUTTON3_DOWN_MASK, MOUSE_WHEEL_UP, MOUSE_WHEEL_DOWN
     * @param includePointer include the pointer (enlarged and indicates button presses)
     * @return image of the screen.
     */
    private static BufferedImage getScreenShot( int active, int buttons, boolean includePointer ) {
        
        long t0= System.currentTimeMillis();
        
        //http://www.javalobby.org/forums/thread.jspa?threadID=16400&tstart=0
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();
        BufferedImage screenshot;

        Rectangle bounds;

        int i = active;

        PointerInfo info= MouseInfo.getPointerInfo();
        Point mousePointerLocation= info.getLocation();
        bounds= gs[i].getDefaultConfiguration().getBounds();
        Rectangle b= bounds;
        try {
            screenshot = new Robot(gs[i]).createScreenCapture(bounds);
        } catch (AWTException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            screenshot = new BufferedImage( gs[i].getDisplayMode().getWidth(), gs[i].getDisplayMode().getHeight(), BufferedImage.TYPE_INT_ARGB );
        }

        boolean appContainsPointer= filterBackground( (Graphics2D)screenshot.getGraphics(), b, mousePointerLocation );
        
        boolean screenHasPointer= info.getDevice()==gs[i];

        if ( includePointer ) {
            if ( screenHasPointer && appContainsPointer ) {
                // get the mouse info before grabbing the screenshot, which takes several hundred millis.
                BufferedImage pointer;
                if ( ( button & MouseEvent.BUTTON1_DOWN_MASK ) == MouseEvent.BUTTON1_DOWN_MASK ) {
                    pointer= pnt_b1;
                } else if ( ( button & MouseEvent.BUTTON2_DOWN_MASK ) == MouseEvent.BUTTON2_DOWN_MASK ) {
                    pointer= pnt_b2;
                } else if ( ( button & MouseEvent.BUTTON3_DOWN_MASK ) == MouseEvent.BUTTON3_DOWN_MASK ) {
                    pointer= pnt_b3;
                } else if ( ( button & MOUSE_WHEEL_UP ) == MOUSE_WHEEL_UP ) {
                    pointer= pnt_w1;
                    button= 0;
                } else if ( ( button & MOUSE_WHEEL_DOWN ) == MOUSE_WHEEL_DOWN ) {
                    pointer= pnt_w2;
                    button= 0;
                } else {
                    pointer= pnt;
                }
                
                screenshot.getGraphics().drawImage( pointer, mousePointerLocation.x - b.x - ptrXOffset, mousePointerLocation.y - b.y - ptrYOffset, null );
            }
        }
        
        logger.log(Level.FINE, "got screenshot in {0} ms", (System.currentTimeMillis()-t0));
        return screenshot;
    }

    int active= -1;
    private final Window parent;

    /**
     * the part of the display that has been affected during the capture.
     */
    Rectangle bounds= null;

    /**
     * take a screenshot and write it to a png file.
     * @param t1 the time in millis.
     * @param dt elased time.
     * @param id the event id number.
     * @return 
     */
    private Rectangle doTakePicture( long t1, long dt, int id ) {
        t0= t1;

        final File file = new File( outLocationFolder, tp.format(TimeUtil.now(), null) + "_" + String.format("%06d", dt/100 ) + "_" + String.format("%05d", id ) + ".png" );

        final BufferedImage im = getScreenShot( active, button, true );

        Rectangle b= getScreenBounds(active);
        Rectangle myBounds= getMyBounds(b);

        try {
            if ( !file.createNewFile() ) {
                logger.log(Level.WARNING, "failed to create new file {0}", file);
            } else {
                ImageIO.write(im, "png", file);
            }

        } catch ( Exception ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }

        t0= System.currentTimeMillis();

        return myBounds;
    }

    int keyEscape=0;

    private boolean canReject( AWTEvent theEvent ) {
        boolean reject= true;
        String ps= ((java.awt.event.InvocationEvent)theEvent).paramString();
        if ( this.peekEvent(1200)==null ) {
            // we want to use this if it will cause repaint.
            if ( ps.contains("ComponentWorkRequest") ) { // nasty!
                reject= false;
            } else if ( ps.contains("ProcessingRunnable") ) {
                reject= false;
            }

        } else {

        }
        return reject;
    }

    private static void setButton( int b ) {
        button= b;
    }
    
    @Override
    public void dispatchEvent(AWTEvent theEvent) {

        super.dispatchEvent(theEvent);

        long t1 = System.currentTimeMillis();
        long dt= t1 - tb;
        
        boolean reject= false;

        //List keep= Arrays.asList( PaintEvent.PAINT, PaintEvent.UPDATE, 204, 205, 46288 );
        // 507=MouseWheelEvent
        // 46288=DasUpdateEvent
        List skip= Arrays.asList( 1200, Event.MOUSE_MOVE, Event.MOUSE_DRAG, 
                Event.MOUSE_DOWN, Event.MOUSE_UP, 
                Event.MOUSE_ENTER, Event.MOUSE_EXIT, 
                507, 101, 1005, 400, 401, 402 );

        if ( skip.contains( theEvent.getID() ) ) {
            reject= true;
            if ( theEvent.getID()==1200 ) {
                reject= canReject(theEvent);
            }
        } else {
            
        }

        reject= reject || ( (t1 - t0) < 200);

        try {
            logFile.write(String.format("%09.3f %1d %5d %s\n", 
                    dt / 1000., reject ? 0 : 1, theEvent.getID(), 
                    theEvent.getClass().getName()));
            logFile.flush();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
        }

        if ( theEvent instanceof MouseEvent ) {
            MouseEvent me= (MouseEvent)theEvent;
            if ( me.getModifiersEx()!=0 ) {
                if ( ( me.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK ) == MouseEvent.BUTTON1_DOWN_MASK ) {
                    setButton( MouseEvent.BUTTON1_DOWN_MASK );
                } else if ( ( me.getModifiersEx() & MouseEvent.BUTTON2_DOWN_MASK ) == MouseEvent.BUTTON2_DOWN_MASK ) {
                    setButton( MouseEvent.BUTTON2_DOWN_MASK );
                } else if ( ( me.getModifiersEx() & MouseEvent.BUTTON3_DOWN_MASK ) == MouseEvent.BUTTON3_DOWN_MASK ) {
                    setButton( MouseEvent.BUTTON3_DOWN_MASK );
                }
                reject= false;
            } else {
                if ( theEvent instanceof MouseWheelEvent ) {
                    MouseWheelEvent mwe= (MouseWheelEvent)theEvent;
                    button= mwe.getWheelRotation()<0 ? MOUSE_WHEEL_UP : MOUSE_WHEEL_DOWN;
                    reject= false;
                } else {
                    button= 0;
                    reject= true;
                }
            }
        } 

        if (  !reject ) {
            Rectangle r= doTakePicture( t1, dt, theEvent.getID() );   // Take a picture here
            if ( bounds==null ) bounds= r; else bounds= bounds.union(r);
        }

        // 400 401 402 are Key events.
        if ( theEvent.getID()==Event.MOUSE_MOVE || theEvent.getID()==400 
                || theEvent.getID()==401 || theEvent.getID()==402 ) {
            tickleTimer.tickle( String.valueOf(theEvent.getID()) );
        }

        if ( theEvent.getID()==401 ) {
            if ( ( (KeyEvent)theEvent).getKeyCode()==KeyEvent.VK_CONTROL ) {
                keyEscape=2;
            } else if ( ((KeyEvent)theEvent).getKeyCode()==KeyEvent.VK_SHIFT ) {
                keyEscape--;
                if ( keyEscape==0 ) {
                    pop();

                    Runnable run= new Runnable() {
                        @Override
                        public void run() {
                            finishUp();
                        }
                    };
                    new Thread(run).start();
                }
            }
        } else if ( theEvent.getID()==402 ) {
            if ( ((KeyEvent)theEvent).getKeyCode()==KeyEvent.VK_SHIFT ) {
                // do nothing
            } else {
                keyEscape= 0;
            }
        }

    }

    /**
     * this should not be run on the event thread.
     */
    private void finishUp() {

        JPanel p= new JPanel();
        p.setLayout( new BorderLayout() );
        int count= outLocationFolder.list().length;
        p.add( new JLabel( "<html>Screenshots have been recorded to "+outLocationFolder+
                ".<br>Operation should now be normal.<br><br>Enter Pngwalk?" ), BorderLayout.CENTER );
        JCheckBox cb= new JCheckBox( String.format( "first trim %d images", count ) );
        p.add( cb, BorderLayout.SOUTH );
        if ( JOptionPane.YES_OPTION== AutoplotUtil.showConfirmDialog( parent,
            p,
            "Record Screenshots", JOptionPane.YES_NO_OPTION ) ) {
            if ( cb.isSelected() ) {
                try {
                    DasProgressPanel monitor= DasProgressPanel.createFramed( SwingUtilities.getWindowAncestor(cb), "trimming images..." );
                    trimAll( outLocationFolder, bounds, monitor );
                } catch ( IOException ex ) {

                }
            }
            PngWalkTool tool= PngWalkTool.start( "file:"+outLocationFolder+ "/*.png", null );
            if ( !PngWalkTool.isQualityControlEnabled() ) {
                tool.startQC();
            }
        } else {
            if ( cb.isSelected() ) {
                try {
                    DasProgressPanel monitor= DasProgressPanel.createFramed( SwingUtilities.getWindowAncestor(cb), "trimming images..." );
                    trimAll( outLocationFolder, bounds, monitor );
                } catch ( IOException ex ) {

                }
            }
        }
    }
}
