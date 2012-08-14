package org.autoplot.pngwalk;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.das2.datum.DatumRange;
import org.das2.system.RequestProcessor;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.virbo.datasource.DataSetURI;
import org.virbo.datasource.URISplit;

/**
 * A class to manage an image for the PNGWalk tool. Handles downloading and
 * thumbnail generation.
 * 
 * @author Ed Jackson
 */
public class WalkImage  {

    public static final String PROP_STATUS_CHANGE = "status"; // this should to be the same as the property name to be beany.
    public static final String PROP_BADGE_CHANGE = "badgeChange";

    public static final int THUMB_SIZE = 400;
    private static final int SQUASH_FACTOR = 10; // amount to horizontally squash thumbs for covers and contextFlow
    
    /**
     * number of full-size images we can load at once.
     */
    private static final int LOADED_IMAGE_COUNT_LIMIT = 10;
    
    /**
     * number of full-size images we can load at once.
     */
    private static final int LOADED_THUMB_COUNT_LIMIT = 400;

    final String uriString;  // Used for sorting
    private URI imgURI;

    /**
     * full-size image
     */
    private BufferedImage im;

    /**
     * the thumbnail, typically 400 pixels corner-to-corner.
     */
    private BufferedImage thumb;

    private Dimension thumbDimension;

    /**
     * like thumbnail, but squished horizontally to support coverflow.
     */
    private BufferedImage squishedThumb;

    /**
     *  like thumbnail, but based on the current size.
     */
    private BufferedImage sizeThumb;

    /**
     * the current size of sizeThumb.
     */
    private int sizeThumbWidth=-1;

    private String caption;
    private Status status;
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);
    private final boolean haveThumbs400;

    private static BufferedImage missingImage = initMissingImage();

    private static final LinkedList<WalkImage> freshness= new LinkedList();
    private static final LinkedList<WalkImage> thumbLoadingQueue= new LinkedList();
    private static Runnable thumbLoadingQueueRunner;
    private static final LinkedList<WalkImage> thumbFreshness= new LinkedList();

    //private java.util.concurrent.ThreadPoolExecutor reqProc= new ThreadPoolExecutor( 2, 4, 1, TimeUnit.MINUTES, workQueue );
    
    public URI getUri() {
        if (imgURI != null) {
            return imgURI;
        } else {
            try {
                return new URI("");
            } catch(URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public enum Status {
        UNKNOWN,
        SIZE_THUMB_LOADED, // only the actual size thumb is loaded, because the bigger thumbnail was unloaded.  ThumbDimension will be loaded.
        THUMB_LOADING, // loading only thumbnail
        THUMB_LOADED, // thumbnail loaded but image is not
        IMAGE_LOADING, // loading thumbnail AND image
        IMAGE_LOADED, // this also implies thumbnail is loaded.  Note images can be unloaded, so we might return to the THUMB_LOADED state.
        MISSING,
        ERROR;
    }

    /**
     *
     * @param uri the image URI, or null if this is a placeholder.
     * @param haveThumbs400
     */
    public WalkImage( URI uri, boolean haveThumbs400 ) {
        imgURI = uri;
        this.haveThumbs400= haveThumbs400;
        if (imgURI != null) {
            uriString = DataSetURI.fromUri(uri);
            status = Status.UNKNOWN;
        } else {
            uriString = null;
            status = Status.MISSING;
        }

        // image and thumbnail are initialized lazily
    }

    public Status getStatus() {
        return status;
    }

    private void setStatus(Status s) {
        Status oldStatus = status;
        status = s;
        pcs.firePropertyChange(PROP_STATUS_CHANGE, oldStatus, status);
    }

    public void setCaption(String caption) {
        this.caption = caption;
    }

    public String getCaption() {
        return caption;
    }

    DatumRange dr;

    public DatumRange getDatumRange() {
        return dr;
    }

    public void setDatumRange( DatumRange dr ) {
        this.dr= dr;
    }

    public BufferedImage getImage() {
        if (status == Status.MISSING) {
            return missingImage;
        }
        if ( im == null && status != Status.IMAGE_LOADING) {
            loadImage();
        }

        synchronized (freshness ) {
            freshness.remove(this); // move to freshest position
            freshness.addFirst(this);
        }
        return im;
    }

    /**
     * provide access to the image, but don't load it.
     * @return null or the loaded image.
     */
    BufferedImage getImageIfLoaded() {
        return im;
    }

    public BufferedImage getThumbnail() {
        return getThumbnail(true);
    }

    /**
     * returns the thumbnail if available, or if waitOk is true then it computes it.  Otherwise PngWalkView.loadingImage
     * is returned.
     * @param w
     * @param h
     * @param waitOk
     * @return image or  PngWalkView.loadingImage
     */
    public synchronized BufferedImage getThumbnail( int w, int h, boolean waitOk ) {
        synchronized (thumbFreshness ) {
            thumbFreshness.remove(this); // move to freshest position
            thumbFreshness.addFirst(this);
        }

        if ( sizeThumbWidth==w ) {
            return this.sizeThumb;
        } else {
            if ( waitOk ) {
                BufferedImage theThumb= getThumbnail(true);
                if ( theThumb==null ) {
                    return PngWalkView.loadingImage;
                } else {
                    BufferedImageOp resizeOp = new ScalePerspectiveImageOp(theThumb.getWidth(), theThumb.getHeight(), 0, 0, w, h, 0, 1, 1, 0, false);
                    sizeThumb = resizeOp.filter(theThumb, null);
                    sizeThumbWidth= w;
                    return sizeThumb;
                }
            } else {
                return PngWalkView.loadingImage;
            }
        }
    }
    
    /**
     * return a file, that is never type=0.  This was a bug on Windows.
     * @param f
     * @return
     */
    public BufferedImage readImage( File f ) throws IllegalArgumentException, IOException  {
        BufferedImage im= ImageIO.read( f );
        if ( im==null ) { // Bob had pngs on his site that returned an html document decorating.
            System.err.println("fail to read image: "+f );
            return missingImage;
        }
        if ( im.getType()==0 ) {
            BufferedImage imNew= new BufferedImage( im.getWidth(), im.getHeight(), BufferedImage.TYPE_INT_ARGB );
            imNew.getGraphics().drawImage( im, 0, 0, null );
            im= imNew;
        }
        return im;
    }

    /**
     * attempt to create the thumbnail on the current thread.  If there is no
     * "thumbs400" directory and the image is not yet loaded, then no
     * action takes place.
     */
    public void getThumbnailImmediately( ) {
        BufferedImage rawThumb;
        try {
            if ( haveThumbs400!=false ) {
                URI fsRoot = DataSetURI.toUri( URISplit.parse( imgURI ).path + "thumbs400");
                //System.err.println("Thumb path: " + fsRoot);
                FileSystem fs = FileSystem.create(fsRoot);
                String s = DataSetURI.fromUri( imgURI );
                FileObject fo = fs.getFileObject(s.substring(s.lastIndexOf('/') + 1));

                File localFile = fo.getFile();
                rawThumb = readImage(localFile);

                if ( rawThumb==null ) {
                    throw new RuntimeException( "Unable to read: "+localFile );
                }
            } else {
                throw new IOException("silly code to jump");
            }

        } catch (IOException ex) {
            // Assume the error is that the thumbs folder doesn't exist; other errors
            // will occur again in loadImage()
            //System.err.println("Thumb dir doesn't exist; using image.");
            if (im == null) {
                // Otherwise we'll have to create the thumb from the full-sized image
                // Initiate loading and return; clients listen for prop change
                loadImage();  // won't do anything if image is already loading
                return;
            }
            rawThumb = im;

        } catch ( IllegalArgumentException ex ) { // local filesystem throws IllegalArgument when the root does not exist.
            // Assume the error is that the thumbs folder doesn't exist; other errors
            // will occur again in loadImage()
            //System.err.println("Thumb dir doesn't exist; using image.");
            if (im == null) {
                // Otherwise we'll have to create the thumb from the full-sized image
                // Initiate loading and return; clients listen for prop change
                loadImageImmediately();  // won't do anything if image is already loading
                return;
            }
            rawThumb = im;

        }
        double aspect = (double) rawThumb.getWidth() / (double) rawThumb.getHeight();

        int height = (int) Math.round(Math.sqrt((THUMB_SIZE * THUMB_SIZE) / (aspect * aspect + 1)));
        int width = (int) Math.round(height * aspect);

        synchronized ( this ) {
            //BufferedImageOp resizeOp = new ScalePerspectiveImageOp(rawThumb.getWidth(), rawThumb.getHeight(), 0, 0, width, height, 0, 1, 1, 0, false);
            //thumb = resizeOp.filter(rawThumb, null);
            thumb = WalkUtil.resizeImage( rawThumb, width, height );
            thumbDimension= new Dimension(width,height);
            if (status == Status.THUMB_LOADING) {
                setStatus(Status.THUMB_LOADED);
            } else if (status == Status.SIZE_THUMB_LOADED) {
                setStatus(Status.THUMB_LOADED);
            } else {
                //setStatus(Status.THUMB_LOADED);
            }
        }


        synchronized (thumbFreshness ) {
            thumbFreshness.remove(this); // move to freshest position
            thumbFreshness.addFirst(this);
        }

        LinkedList<WalkImage> clear= new LinkedList();
        synchronized ( thumbFreshness ) {
            while ( thumbFreshness.size() > LOADED_THUMB_COUNT_LIMIT ) {
                WalkImage old= thumbFreshness.getLast();
                thumbFreshness.remove(old);
                clear.add(old);
            }
        }

        while ( clear.size()>0 ) {
            WalkImage old= clear.poll();
            synchronized ( old ) {
                Logger.getLogger("org.autoplot.pngwalk.WalkImage").fine( "unloading thumbnail for "+old );
                old.setStatus( Status.SIZE_THUMB_LOADED );
                old.squishedThumb= null;
                old.thumb= null;
                old.im= null;
            }
        }
    }

    public synchronized Dimension getThumbnailDimension( boolean loadIfNeeded ) {
        if ( loadIfNeeded ) {
            getThumbnail(loadIfNeeded);
        }
        return this.thumbDimension;
    }

    /**
     *
     * @param loadIfNeeded if false, then be reluctant to load the thumbnail.
     * @return the thumbnail, or null if it is not loaded.
     */
    public BufferedImage getThumbnail(boolean loadIfNeeded) {

        synchronized (thumbFreshness ) {
            thumbFreshness.remove(this); // move to freshest position
            thumbFreshness.addFirst(this);
        }

        synchronized (this) {
            switch (status) {
                case THUMB_LOADING:
                    // We're already working on it in another thread
                    return null;

                case THUMB_LOADED:
                    if ( thumb==null ) {
                        throw new RuntimeException("thumb should not be null");
                    }
                    return thumb;

                case IMAGE_LOADED:
                    return thumb;

                case ERROR:
                    return thumb;   //TODO: May be null; use error placeholder?

                case MISSING:
                    return missingImage;

                case SIZE_THUMB_LOADED:
                case UNKNOWN:
                    if(!loadIfNeeded) return null;
                    setStatus(Status.THUMB_LOADING);
                    return maybeReturnThumb(loadIfNeeded);

                case IMAGE_LOADING:
                    return maybeReturnThumb(loadIfNeeded);

                default:
                    //should never get here, but keeps Java from warning about missing return
                    throw new IllegalArgumentException("Encountered invalid status in walk image.");
            } //end switch
        }
    }

    private static void maybeStartThumbLoadingQueueRunner() {
        if ( thumbLoadingQueueRunner!=null ) {
            return;
        } else {
            Runnable r= new Runnable() {
                public void run() {
                    while ( WalkImage.thumbLoadingQueue.size()>0 ) {
                        WalkImage image;
                        synchronized ( thumbLoadingQueue ) {
                            //String bar="=======================================================";
                            //String b= thumbLoadingQueue.size()>bar.length() ? bar + " "+bar.length() : bar.substring(0,thumbLoadingQueue.size());
                            //System.err.println("thumbLoadingQueueRunner: " + b );
                            image= thumbLoadingQueue.poll();
                        }
                        image.getThumbnailImmediately();
                    }
                    synchronized ( thumbLoadingQueue ) {
                        //System.err.println("stopping thumbLoadingQueueRunner");
                        WalkImage.thumbLoadingQueueRunner= null;
                    }
                }
            };
            synchronized ( thumbLoadingQueue ) {
                if ( thumbLoadingQueueRunner==null ) {
                    //System.err.println("starting up thumbLoadingQueueRunner");
                    thumbLoadingQueueRunner= r;
                    RequestProcessor.invokeLater(thumbLoadingQueueRunner);
                }
            }
        }
    }

    private BufferedImage maybeReturnThumb(boolean loadIfNeeded) {
        if (thumb != null) return thumb;
        if (!loadIfNeeded) return null;

        if ( false ) {
            synchronized ( thumbLoadingQueue ) {
                thumbLoadingQueue.add( this );
            }
            maybeStartThumbLoadingQueueRunner();
        } else {
            //acquire thumbnail
            Runnable r = new Runnable() {
                public void run() {
                    getThumbnailImmediately();
                }
            };
            RequestProcessor.invokeLater(r);
        }
      //  //acquire thumbnail
      //  Runnable r = new Runnable() {
      //      public void run() {
      //          getThumbnailImmediately();
      //      }
      //  };
      //  RequestProcessor.invokeLater(r);
        return null;
    }

    public BufferedImage getSquishedThumbnail() {
        return getSquishedThumbnail(true);
    }

    /**
     *
     * @param loadIfNeeded if false, then be reluctant to load the thumbnail.
     * @return
     */
    public BufferedImage getSquishedThumbnail( boolean loadIfNeeded ) {
        if (status == Status.MISSING)
            return missingImage;
        if (squishedThumb == null) {
            synchronized ( this ) {
                if (thumb == null) {
                    if (getThumbnail(loadIfNeeded) == null) {
                        return null;
                    }
                }
                if (thumb != null) {  //might be loading on another thread
                    BufferedImageOp resizeOp = new ScalePerspectiveImageOp(thumb.getWidth(), thumb.getHeight(), 0, 0, thumb.getWidth() / SQUASH_FACTOR, thumb.getHeight(), 0, 1, 1, 0, false);
                    squishedThumb = resizeOp.filter(thumb, null);
                }
            }
        }
        return squishedThumb;
    }

    private void loadImageImmediately() {
        try {
            //System.err.println("download "+imgURI );

            URI fsRoot = DataSetURI.toUri( URISplit.parse(imgURI).path );
            FileSystem fs = FileSystem.create(fsRoot);

            String s = DataSetURI.fromUri(imgURI);
            FileObject fo = fs.getFileObject(s.substring(s.lastIndexOf('/') + 1));

            File localFile = fo.getFile();

            Thread.yield();

            im = readImage(localFile);

            if ( im==null ) {
                throw new RuntimeException( "unable to read: "+localFile );
            }

            synchronized (freshness ) {
                freshness.remove(this); // move to freshest position
                freshness.addFirst(this);
            }

            LinkedList<WalkImage> clear= new LinkedList();
            synchronized ( freshness ) {
                while ( freshness.size() > LOADED_IMAGE_COUNT_LIMIT ) {
                    WalkImage old= freshness.getLast();
                    freshness.remove(old);
                    clear.add(old);
                }
            }

            while ( clear.size()>0 ) {
                WalkImage old= clear.poll();
                synchronized ( old ) {
                    Logger.getLogger("org.autoplot.pngwalk.WalkImage").fine( "unloading image for "+old );
                    old.im= null;
                    if ( old.getStatus()==Status.IMAGE_LOADED ) { //bugfix: unloading the thumbnails might have set status to SIZE_THUMB_LOADED
                         old.setStatus( Status.THUMB_LOADED );
                    }
                }
            }

            BufferedImage lthumb=null;
            synchronized (this) {
                lthumb= thumb;
            }

            if (lthumb == null) {
                getThumbnailImmediately(); //force thumbnail creation
            }

            setStatus(Status.IMAGE_LOADED);

        } catch (Exception ex) {
            System.err.println("Error loading image file from " + DataSetURI.fromUri(imgURI) );
            Logger.getLogger(WalkImage.class.getName()).log(Level.SEVERE, null, ex);
            setStatus(Status.MISSING);
            throw new RuntimeException(ex);
        }

    }

    private void loadImage() {
        if (status == Status.IMAGE_LOADING || status == Status.IMAGE_LOADED) {
            return;
        }
        Runnable r = new Runnable() {
            public void run() {
                loadImageImmediately();
            }
        };
        setStatus(Status.IMAGE_LOADING);
        RequestProcessor.invokeLater(r);
    }

    private static BufferedImage initMissingImage() {
        BufferedImage missing = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = missing.createGraphics();
        g2.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );
        g2.setRenderingHint( RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON );
        g2.setColor(java.awt.Color.GRAY);
        FontMetrics fm = g2.getFontMetrics(g2.getFont());
        String msg = "(Missing)";
        g2.drawString(msg, (200 - fm.stringWidth(msg)) / 2, 100);

        return missing;
    }
    // Implementing the Comparable interface lets List sort
    //TODO: Compare on date information first
/*public int compareTo(WalkImage o) {
    return imgURI.compareTo(o.imgURI);
    }*/
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }

    @Override
    public String toString() {
        return this.getCaption()==null ? this.uriString : this.getCaption();
    }
}
