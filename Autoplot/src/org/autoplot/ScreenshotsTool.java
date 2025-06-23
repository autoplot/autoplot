
package org.autoplot;

import java.awt.AWTEvent;
import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
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
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.autoplot.datasource.AutoplotSettings;
import org.autoplot.pngwalk.PngWalkTool;
import org.das2.components.DasProgressPanel;
import org.das2.datum.LoggerManager;
import org.das2.datum.TimeParser;
import org.das2.datum.TimeUtil;
import org.das2.datum.Units;
import org.das2.util.FileUtil;
import org.das2.util.monitor.NullProgressMonitor;
import org.das2.util.monitor.ProgressMonitor;
import org.autoplot.util.TickleTimer;

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
    
    private boolean receivedEvents= false;  // so this can be used without automatic screenshots.

    long t0 = 0;
    
    /**
     * intialization time
     */
    long timeBase = System.currentTimeMillis();

    private final ConcurrentLinkedQueue<ImageRecord> imageQueue= new ConcurrentLinkedQueue<>();
    
    private final Object imageQueueLock= new Object();
    
    /**
     * thread responsible for converting BufferedImages into files.
     */
    Thread pngWriterThread;
            
    boolean pngWriterThreadRunning= false;
    boolean pngWriterThreadNotDone= false;
    /*
     * block&gt;0 means decrement.  block&lt;0 means wait.
     */
    int block= 0;
    
    File outLocationFolder;
    BufferedWriter logFile;
    TimeParser tp = TimeParser.create("$Y$m$d_$H$M$S_$(milli)");
    TickleTimer tickleTimer;
    
    private static void checkFolderContents( String text, JCheckBox deleteFilesCheckBox ) {
        File f= new File( text );
        if ( f.exists() ) {
            File[] ff= f.listFiles();
            if ( ff!=null && ff.length>1 ) {
                deleteFilesCheckBox.setEnabled(true);
            } else {
                deleteFilesCheckBox.setEnabled(false);
                deleteFilesCheckBox.setSelected(false);
            }
        } else {
            deleteFilesCheckBox.setEnabled(false);
            deleteFilesCheckBox.setSelected(false);
        }
    }
    
    /**
     * start should be called from the event thread.
     * @param parent the device
     */
    public static void start( Window parent ) {

        Preferences prefs= AutoplotSettings.settings().getPreferences( ScreenshotsTool.class );
        String s= prefs.get( "outputFolder", System.getProperty("user.home") );

        JPanel p= new JPanel();
        p.setLayout( new BoxLayout(p,BoxLayout.PAGE_AXIS ) );

        p.add( new JLabel( "<html>This will automatically take screenshots, recording them to a folder.<br><br>Hold Ctrl and press Shift twice to stop recording, <br>or Hold Alt and press Shift twice." ), JLabel.LEFT_ALIGNMENT );

        JPanel folderPanel= new JPanel();
        folderPanel.setLayout( new FlowLayout() );
        folderPanel.add( new JLabel( "Output Folder:" ) );
        final JTextField tf= new JTextField(20);
        tf.setText(s);
        folderPanel.add( tf );

        folderPanel.add( new JButton( new AbstractAction( "Pick", new ImageIcon( ScreenshotsTool.class.getResource("/org/autoplot/file.png") ) ) {
            @Override
            public void actionPerformed(ActionEvent e) {
                JFileChooser ch= new JFileChooser();
                ch.setFileSelectionMode( JFileChooser.DIRECTORIES_ONLY );
                ch.setCurrentDirectory( new File( tf.getText() ).getParentFile() );
                ch.setSelectedFile( new File( tf.getText() ) );
                if ( ch.showSaveDialog(tf)==JFileChooser.APPROVE_OPTION ) {
                    tf.setText(ch.getSelectedFile().toString());
                }
            }
        }));
        folderPanel.setAlignmentX( Component.LEFT_ALIGNMENT );
        p.add( folderPanel );

        final JCheckBox deleteFilesCheckBox= new JCheckBox("Delete contents before starting");
        deleteFilesCheckBox.setEnabled(false);
        deleteFilesCheckBox.setAlignmentX( Component.LEFT_ALIGNMENT );
        checkFolderContents(tf.getText(),deleteFilesCheckBox);
            
        tf.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                checkFolderContents(tf.getText(),deleteFilesCheckBox);
            }
        } );
        
        tf.addFocusListener( new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                checkFolderContents(tf.getText(),deleteFilesCheckBox);
            }
            @Override
            public void focusLost(FocusEvent e) {
                checkFolderContents(tf.getText(),deleteFilesCheckBox);
            }
        });
        tf.getDocument().addDocumentListener( new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                checkFolderContents(tf.getText(),deleteFilesCheckBox);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                checkFolderContents(tf.getText(),deleteFilesCheckBox);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                checkFolderContents(tf.getText(),deleteFilesCheckBox);
            }
        } );
        
        p.add( deleteFilesCheckBox );
        
        int r= JOptionPane.showConfirmDialog( parent, p,
        "Record Screenshots",
        JOptionPane.OK_CANCEL_OPTION );

        if ( r==JOptionPane.OK_OPTION ) {
            File f= new File( tf.getText() );
            if ( f.exists() ) {
                File[] ff= f.listFiles();
                if ( ff!=null && ff.length>1 && deleteFilesCheckBox.isSelected() ) {
                    if ( !FileUtil.deleteFileTree(f) ) {
                        JOptionPane.showMessageDialog(parent,"Unable to delete files");
                    }
                }
            }
            try {
                prefs.put( "outputFolder", tf.getText() );
                try{
                    prefs.flush();
                } catch ( BackingStoreException e ) {
                    logger.log( Level.WARNING, e.getMessage(), e );
                }
                Toolkit.getDefaultToolkit().getSystemEventQueue().push(
                    new ScreenshotsTool( parent, tf.getText() ));
            } catch ( IOException ex ) {
                throw new RuntimeException(ex);
            }
        }
    }

    /**
     * create a new ScreenshotsTool, which will write screenshots to the location.
     * @param parent parent to follow.  It and its children are recorded.
     * @param outLocationFolder local file location, folder relative to Autoplot's PWD.
     * @throws IOException 
     * @see #ScreenshotsTool(java.awt.Window, java.lang.String, boolean) 
     */
    public ScreenshotsTool( Window parent, String outLocationFolder ) throws IOException {
        this( parent, outLocationFolder, false );
    }
    
    /**
     * create a new ScreenshotsTool, which will write screenshots to the location.  The
     * output folder must not exist or be empty, or clearFolder must be set to true.
     * This is created and then pushed to the event stack, so that screenshots will
     * be taken when activity occurs (see start which manages this), or will takePicture
     * is called to manually take screenshots (e.g. from scripts).  When the
     * session is done, requestFinish is called to clean up.
     * @param parent parent to follow.  It and its children are recorded.
     * @param outLocationFolder local file location, folder relative to Autoplot's PWD.
     * @param clearFolder if true, clear any files from the output folder.
     * @throws IOException 
     * @see #takePicture(int) 
     * @see #takePicture(int, java.lang.String) which writes the caption to a PNGWalk QC file.
     * @see #requestFinish(boolean) 
     */
    public ScreenshotsTool( Window parent, String outLocationFolder, boolean clearFolder ) throws IOException {
        this.outLocationFolder= new File( outLocationFolder );
        boolean fail= false;
        if (!this.outLocationFolder.exists()) {
            fail= !this.outLocationFolder.mkdirs();
        }
        if ( fail ) throw new IOException("output folder cannot be created");
        File[] ff= this.outLocationFolder.listFiles();
        if ( ff!=null && ff.length>1 ) {
            if ( clearFolder ) {
                for ( File f:ff ) {
                    if ( !f.delete() ) throw new IllegalArgumentException("unable to delete file: "+f );
                }
            } else {
                throw new IOException("output folder must be empty");
            }
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
                    Rectangle r= doTakePicture(filenameFor(t1, 99999), t1);
                    if ( bounds==null ) bounds= r; else bounds= bounds.union(r);

                } else {
                    if ( canReject(update) ) {
                        long t1= System.currentTimeMillis();
                        Rectangle r= doTakePicture(filenameFor(t1, 99999), t1);
                        if ( bounds==null ) bounds= r; else bounds= bounds.union(r);
                    }
                    //System.err.println("update coming anyway");
                }
            }
        } );

        pngWriterThreadRunning= true;
        
        pngWriterThread = new Thread( new Runnable() {
            @Override
            public void run() {
                try {
                    logger.log(Level.FINE, "starting imageRecorderThread" );
                    while ( pngWriterThreadRunning || !imageQueue.isEmpty() ) {
                        logger.log(Level.FINE, "imageRecorderThread..." );

                        while ( pngWriterThreadRunning && imageQueue.isEmpty() ) { 
                            synchronized ( imageQueueLock ) {
                                if ( pngWriterThreadRunning && imageQueue.isEmpty() ) {
                                    imageQueueLock.wait();
                                }
                            }
                        }
                        while ( !imageQueue.isEmpty() ) {
                            logger.log(Level.FINER, "imageQueue length={0}", imageQueue.size());
                            ImageRecord record= imageQueue.remove();
                            logger.log(Level.FINE, "imageRecorder writing {0}", record.filename);

                            try {
                                if ( !record.filename.createNewFile() ) {
                                    logger.log(Level.FINE, "file already exists: {0}", record.filename);
                                } else {
                                    ImageIO.write( record.image, "png", record.filename);
                                }

                            } catch ( IOException ex ) {
                                logger.log( Level.WARNING, ex.getMessage(), ex );
                            }

                            logger.log(Level.FINE, "formatted file in {0}ms", ( System.currentTimeMillis()-t0 ));

                        }
                    }
                    logger.fine("sleep for a second to make sure the last image is done writing."); // test.Test_042_TwoTsb would show issue
                    Thread.sleep(1000);
                    synchronized (imageQueueLock) {
                        pngWriterThreadNotDone= false;
                    }
                } catch ( InterruptedException ex ) {
                    logger.log( Level.WARNING, null, ex );
                }
            };
        } );
        
        pngWriterThread.start();
        
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
    
    private static class ImageRecord {
        ImageRecord( BufferedImage image, File filename ) {
            this.image= image;
            this.filename= filename;
        }
        BufferedImage image;
        File filename;
    }
    
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
     * identify parts of the desktop that are Autoplot, for the user's privacy.
     * @param g the graphics to paint on.
     * @param b the rectangle showing the display translation.
     * @param active the list which will be populates
     * @return the rectangle union of all frames, in the same frame as mouse events.
     */
    private static Rectangle getActiveBackground( List<Rectangle> active ) {
        
        long t0= System.currentTimeMillis();
        
        Rectangle r= null;

        boolean containsPointer= false;
        
        Frame[] frames = Frame.getFrames();
        for (Frame frame : frames) {
            if ( frame.isVisible() ) {
                if( frame.getExtendedState() != Frame.ICONIFIED ) {
                    Rectangle rect= frame.getBounds();
                    logger.log(Level.FINER, "showing {0} {1}", new Object[]{rect, frame.getTitle()});
                    if ( r==null ) r=rect; else r.add( rect );
                    active.add( rect );
                }
            }
        }

        Window[] windows= Window.getWindows();
        for ( Window window: windows ) {
            if ( window.isVisible() ) {
                if ( window.isShowing() ) {
                    Rectangle rect= window.getBounds();
                    logger.log(Level.FINER, "showing {0} {1}", new Object[]{rect, window.getType()});
                    if ( r==null ) r=rect; else r.add( rect );
                    active.add( rect );
                }
            }
        }
        
        logger.log(Level.FINE, "getActiveBackground in {0}ms", (System.currentTimeMillis()-t0));
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

        Rectangle r= new Rectangle(0,0,b.width,b.height);

        Area s= new Area(r);

        boolean containsPointer= false;
        
        Frame[] frames = Frame.getFrames();
        for (Frame frame : frames) {
            if ( frame.isVisible() ) {
                if( frame.getExtendedState() != Frame.ICONIFIED ) {
                    Rectangle rect= frame.getBounds();
                    //rect= new Rectangle( rect.x-1, rect.y-1, rect.width+2, rect.height+2 ); // My Linux doesn't put nice borders around GUIs.
                    logger.log(Level.FINER, "showing {0} {1}", new Object[]{rect, frame.getTitle()});
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
                    logger.log(Level.FINER, "showing {0} {1}", new Object[]{rect, window.getType()});
                    if ( rect.contains(p) ) containsPointer=true;
                    rect.translate( -b.x, -b.y );
                    s.subtract( new Area( rect ) );
                }
            }
        }

        g.fill(s);
        g.setColor(Color.GRAY);
        g.draw(s);
        g.setColor(Color.WHITE);
        g.draw(r);
        
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
        if ( ff==null ) throw new IllegalArgumentException("directory cannot be read: "+root); // this won't happen but we do this for findbugs.
        Rectangle result= null;
        int c= ff.length;
        int i= 1;
        monitor.setTaskSize(c);
        monitor.setProgressMessage( "find bounds for set");
        for ( File f : ff ) {
            logger.log(Level.FINE, "getTrim {0}", f.getName());
            monitor.setTaskProgress( i );
            if ( f.toString().endsWith(".png") ) {
                try {
                    BufferedImage im= ImageIO.read(f);
                    Rectangle r1= getTrim( im );
                    if ( result==null ) {
                        result= r1;
                    } else {
                        result= result.union(r1);
                    }
                } catch ( RuntimeException ex ) {
                    throw new RuntimeException("failed to read "+f.toString(),ex);
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
        if ( ff==null ) throw new IllegalArgumentException("directory cannot be read: "+dir); // this won't happen but we do this for findbugs.

        monitor.started();

        if ( r==null ) r= getTrim( dir, monitor );
        monitor.setProgressMessage("trim images");
        monitor.setTaskSize( ff.length );
        int i=0;
        for ( File f : ff ) {
            logger.log(Level.FINER, "trim {0}", f);
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
     * @see #getScreenShot(java.awt.Window) 
     * @return 
     */
    public static BufferedImage getScreenShot( ) {
        throw new IllegalArgumentException("getScreenShot now needs a window");
    }
    
    /**
     * @see #getScreenShotNoPointer(java.awt.Window) 
     * @return 
     */
    public static BufferedImage getScreenShotNoPointer( ) {
        throw new IllegalArgumentException("getScreenShotNoPointer now needs a window");
    }
    
    /**
     * get a screenshot of the display Autoplot's main UI is running within.
     * @param w the window parent of all other windows.
     * @return
     */
    public static BufferedImage getScreenShot( Window w ) {
        int active= getActiveDisplay(w);
        return getScreenShot(active,0,true);
    }

    /** 
     * get a screenshot of the display Autoplot's main UI is running within, but without the pointer.
     * @param w the window parent of all other windows.
     * @return
     */
    public static BufferedImage getScreenShotNoPointer( Window w ) {
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
        Rectangle b= new Rectangle(bounds);

        List<Rectangle> activeRects= new ArrayList<>();
        
        Rectangle appRect= getActiveBackground( activeRects );
        
        boolean screenHasPointer= info.getDevice()==gs[i];
        boolean appContainsPointer= appRect.contains(mousePointerLocation);
        
        try {
            long t1= System.currentTimeMillis();
            
            logger.log(Level.FINER, "getting screenshot from screen {0}.", i);
            screenshot = new Robot(gs[i]).createScreenCapture(bounds);
            logger.log(Level.FINER, "got screenshot from screen {0} in {1}ms.", new Object[]{i, System.currentTimeMillis()-t1});
            
            boolean allBlack= true;
            if ( bounds.x>0 ) {
                int lastX= bounds.width; // appRect.x+appRect.width;
                int lastY= bounds.height; //appRect.y+appRect.height;
                for ( int ii=0; ii<lastX && allBlack; ii++ ) {
                    for ( int jj=0; jj<lastY; jj++ ) {
                        if ( screenshot.getRGB(ii,jj)!=0 ) {
                            allBlack= false;
                            break;
                        }
                    }
                }
                if ( allBlack ) {
                    if ( gs.length==2 ) {
                        screenshot = new Robot(gs[1-i]).createScreenCapture(bounds);
                    } else {
                        bounds.translate(-bounds.x,-bounds.y);
                        screenshot = new Robot(gs[i]).createScreenCapture(bounds);
                    }
                }
            }
        } catch (AWTException ex) {
            logger.log(Level.SEVERE, ex.getMessage(), ex);
            screenshot = new BufferedImage( gs[i].getDisplayMode().getWidth(), gs[i].getDisplayMode().getHeight(), BufferedImage.TYPE_INT_ARGB );
        }

        logger.log(Level.FINER, "got screenshot at {0}ms", (System.currentTimeMillis()-t0));
        
        filterBackground( (Graphics2D)screenshot.getGraphics(), b, mousePointerLocation );
        //boolean appContainsPointer= true;
        
        if ( includePointer ) {
            if ( screenHasPointer && appContainsPointer ) {
                int pntrX, pntrY; 
                BufferedImage pointer;
        
                // get the mouse info before grabbing the screenshot, which takes several hundred millis.
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
                logger.log(Level.FINER, "pointer identified at {0}ms", (System.currentTimeMillis()-t0));
                pntrX= mousePointerLocation.x - b.x - ptrXOffset;
                pntrY= mousePointerLocation.y - b.y - ptrYOffset;
                
                screenshot.getGraphics().drawImage( pointer, pntrX, pntrY, null );
                logger.log(Level.FINER, "pointer drawn at {0}ms", (System.currentTimeMillis()-t0));
            }
        }
        
        logger.log(Level.FINE, "got screenshot in {0}ms", (System.currentTimeMillis()-t0));
        return screenshot;
    }

    int active= -1;
    private final Window parent;

    /**
     * the part of the display that has been affected during the capture.
     */
    Rectangle bounds= null;

    /**
     * create a filename for the given time offset and event id.
     * @param dt milliseconds since initial time.
     * @param id event id
     * @return the filename to use, currently $Y$m$d_$H$M$S_$(subsec,places=3)_id.png
     */
    private String filenameFor( long t1, int id ) {
        double us2000= (t1 - 946684800e3 ) * 1000;
        return tp.format(Units.us2000.createDatum(us2000)) + "_" + String.format("%05d", id ) + ".png";
    }
    
    /**
     * manually trigger a screenshot, which is put in the output directory.
     * @param id user-provided id (&le; 99999) for the image, which is the last part of the filename.
     */
    public void takePicture( int id ) {
        long t1= System.currentTimeMillis();
        doTakePicture(filenameFor(t1, id), t1);
    }
    
    /**
     * manually trigger a screenshot, which is put in the output directory, and 
     * write a QC file to contain a caption.
     * @param id user-provided id (&le; 99999) for the image, which is the last part of the filename.
     * @param caption string caption.
     */
    public void takePicture( int id, String caption ) {
        long t1= System.currentTimeMillis();
        String file= filenameFor(t1, id);
        doTakePicture(file, t1,false);
        String reviewer= System.getProperty("user.name");
        String time= TimeParser.create("$Y-$m-$dT$H:$M:$SZ").format(TimeUtil.now());
        String s= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<qualityControlRecord xmlns=\"http://autoplot.org/data/schema/pngwalkQC\">\n" +  // TODO: xmlns is not valid, and has been renamed to hide.
            "    <currentStatus>OK</currentStatus>\n" +
            String.format( "    <modifiedDate>%s</modifiedDate>\n", time ) +
            String.format( "    <imageURI>%s</imageURI>\n", file ) +
            String.format( "    <reviewComment date=\"%s\" reviewer=\"%s\" status=\"OK\">%s</reviewComment>\n", time, reviewer, caption ) +
            "</qualityControlRecord>" ;
        File f= new File( outLocationFolder, file + ".ok" );
        try ( FileWriter fo= new FileWriter(f) ) {
            fo.write(s);
        } catch ( IOException ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }
        
    }
     
    /**
     * manually trigger a screenshot, which is put in the output directory, and 
     * write a QC file to contain a caption, and draw the pointer focused on the
     * component.
     * @param id user-provided id (&le; 99999) for the image, which is the last part of the filename.
     * @param caption string caption.
     * @param c component for controlling the mouse pointer location.
     * @param p null or the point relative to the component.
     * @param buttons MouseEvent.BUTTON1_DOWN_MASK
     */
    public void takePicture( int id, String caption, Component c, Point p, int buttons ) {

        long t1= System.currentTimeMillis();
        String filename= filenameFor(t1, id);

        if ( p==null ) {
            p= c.getLocation();
            p.translate( 6,  2 * c.getHeight() / 3 ); // 6 pixels to the right, 2/3 of the way down.
        } else {
            p= p.getLocation(); // let's not mutate the user-provided point.
        }
        SwingUtilities.convertPointToScreen( p, c );
        
        t0= t1;

        final File file = new File( outLocationFolder, filename );

        final BufferedImage im = getScreenShot( active, 0, false );

        BufferedImage pointer;
        if ( ( buttons & MouseEvent.BUTTON1_DOWN_MASK ) == MouseEvent.BUTTON1_DOWN_MASK ) {
            pointer= pnt_b1;
        } else if ( ( buttons & MouseEvent.BUTTON2_DOWN_MASK ) == MouseEvent.BUTTON2_DOWN_MASK ) {
            pointer= pnt_b2;
        } else if ( ( buttons & MouseEvent.BUTTON3_DOWN_MASK ) == MouseEvent.BUTTON3_DOWN_MASK ) {
            pointer= pnt_b3;
        } else if ( ( buttons & MOUSE_WHEEL_UP ) == MOUSE_WHEEL_UP ) {
            pointer= pnt_w1;
        } else if ( ( buttons & MOUSE_WHEEL_DOWN ) == MOUSE_WHEEL_DOWN ) {
            pointer= pnt_w2;
        } else {
            pointer= pnt;
        }

        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gs = ge.getScreenDevices();        
        
        Rectangle b= getScreenBounds(active);
        Rectangle r= getMyBounds(b);
        if ( bounds==null ) bounds= r; else bounds= bounds.union(r);
        
        Rectangle bounds1= gs[active].getDefaultConfiguration().getBounds();
        
        im.getGraphics().drawImage( pointer, p.x - bounds1.x - ptrXOffset, p.y - bounds1.y - ptrYOffset, null ); 

        try {
            if ( !file.createNewFile() ) {
                logger.log(Level.WARNING, "failed to create new file {0}", file);
            } else {
                ImageIO.write(im, "png", file);
            }

        } catch ( IOException ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }

        t0= System.currentTimeMillis();
        
        
        
        String reviewer= System.getProperty("user.name");
        String time= TimeParser.create("$Y-$m-$dT$H:$M:$SZ").format(TimeUtil.now());
        String s= "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<qualityControlRecord xmlns=\"http://autoplot.org/data/schema/pngwalkQC\">\n" +  // TODO: xmlns is not valid, and has been renamed to hide.
            "    <currentStatus>OK</currentStatus>\n" +
            String.format( "    <modifiedDate>%s</modifiedDate>\n", time ) +
            String.format( "    <imageURI>%s</imageURI>\n", file ) +
            String.format( "    <reviewComment date=\"%s\" reviewer=\"%s\" status=\"OK\">%s</reviewComment>\n", time, reviewer, caption ) +
            "</qualityControlRecord>" ;
        File f= new File( outLocationFolder, file + ".ok" );
        try ( FileWriter fo= new FileWriter(f) ) {
            fo.write(s);
        } catch ( IOException ex ) {
            logger.log( Level.WARNING, ex.getMessage(), ex );
        }
        
    }

    /**
     * take a screenshot and write it to a png file.
     * @param filename the filename for the image, without the root.
     * @param t1 the time in millis.
     * @param dt elapsed time.
     * @param id the event id number.
     * @return the rectangle containing the GUI window.
     */
    private Rectangle doTakePicture( String filename, long t1 ) {
        return doTakePicture( filename, t1, true );
    }
        
    /**
     * take a screenshot and write it to a png file.
     * @param filename the filename for the image, without the root.
     * @param t1 the time in millis.
     * @param includePointer true if the pointer should be drawn.
     * @return the rectangle containing the GUI window.
     */
    private Rectangle doTakePicture( String filename, long t1, boolean includePointer ) {
        t0= t1;

        final File file = new File( outLocationFolder, filename );

        final BufferedImage im = getScreenShot( active, button, includePointer );

        Rectangle b= getScreenBounds(active);
        Rectangle myBounds= getMyBounds(b);

        ImageRecord imr= new ImageRecord( im, file );
        synchronized ( imageQueueLock ) {
            imageQueue.add( imr );
            imageQueueLock.notifyAll();
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
    
    /**
     * This was introduced to provide a method for Jemmy tests to record videos 
     * (so that videos are tested), but it looks like this won't work.  However
     * this would probably be useful from scripts, so I will leave it.
     * 
     * @param trimAll trim off the portions of all screenshots which are not used.
     */
    public void requestFinish( boolean trimAll ) {
        if ( receivedEvents ) pop();
        pngWriterThreadRunning= false;
        try {
            while ( pngWriterThreadNotDone ) {
                synchronized ( imageQueueLock ) {
                    if ( pngWriterThreadNotDone  ) {
                        imageQueueLock.wait();
                    }
                }
            }
        } catch ( InterruptedException ex ) {
            throw new RuntimeException(ex);
        }
        if ( trimAll ) {
            try {
                trimAll( outLocationFolder, bounds, new NullProgressMonitor() );
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }
    
    @Override
    public void dispatchEvent(AWTEvent theEvent) {

        this.receivedEvents= true;
        
        super.dispatchEvent(theEvent);

        long t1 = System.currentTimeMillis();
        long dt= t1 - timeBase;
        
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
            logFile.write(String.format( Locale.US, "%09.3f %1d %5d %s\n", 
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
            Rectangle r= doTakePicture(filenameFor(t1, theEvent.getID()), t1);   // Take a picture here
            if ( bounds==null ) bounds= r; else bounds= bounds.union(r);
        }

        // 400 401 402 are Key events.
        if ( theEvent.getID()==Event.MOUSE_MOVE 
                || theEvent.getID()==Event.KEY_PRESS 
                || theEvent.getID()==Event.KEY_RELEASE
                || theEvent.getID()==400 ) {
            tickleTimer.tickle( String.valueOf(theEvent.getID()) );
        }

        if ( theEvent.getID()==Event.KEY_PRESS && theEvent instanceof KeyEvent ) {
            int keyCode= ((KeyEvent)theEvent).getKeyCode();
            System.err.println( "keyEscape: "+keyEscape + " theEvent: " + theEvent  );
            if ( keyCode ==KeyEvent.VK_CONTROL || keyCode ==KeyEvent.VK_ALT ) {
                keyEscape=2;
            } else if ( ((KeyEvent)theEvent).getKeyCode()==KeyEvent.VK_SHIFT ) {
                keyEscape--;
                if ( keyEscape==0 ) {
                    pop();
                    Runnable run= () -> {
                        finishUp();
                    };
                    new Thread(run).start();
                } else if ( keyEscape<0 ) {
                    keyEscape= 0;
                }
            }
        } else if ( theEvent.getID()==402 && theEvent instanceof KeyEvent ) {
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

        pngWriterThreadRunning= false;
        try {
            while ( pngWriterThreadNotDone ) {
                synchronized ( imageQueueLock ) {
                    if ( pngWriterThreadNotDone ) {
                        imageQueueLock.wait();
                    }
                }
            }
        } catch ( InterruptedException ex ) {
            throw new RuntimeException(ex);
        }
        JPanel p= new JPanel();
        p.setLayout( new BorderLayout() );
        
        String[] ss= outLocationFolder.list();
        if ( ss==null ) throw new IllegalStateException("unable to list "+outLocationFolder);
        int count= ss.length;
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
            PngWalkTool tool= PngWalkTool.start( "file:"+outLocationFolder+ "/$Y$m$d_$H$M$S_$(subsec;places=3)_$x.png", null );
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
