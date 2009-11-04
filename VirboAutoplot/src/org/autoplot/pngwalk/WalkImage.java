package org.autoplot.pngwalk;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import org.das2.system.RequestProcessor;
import org.das2.util.filesystem.FileObject;
import org.das2.util.filesystem.FileSystem;
import org.virbo.datasource.URISplit;

/**
 * A class to manage an image for the PNGWalk tool. Handles downloading and
 * thumbnail generation.
 * 
 * @author Ed Jackson
 */
public class WalkImage implements Comparable<WalkImage> {

    public static final String PROP_STATUS_CHANGE = "status"; // this should to be the same as the property name to be beany.
    public static final int THUMB_SIZE = 400;
    final String uriString;  // Used for sorting
    private URI imgURI;
    private BufferedImage im;
    private BufferedImage thumb;
    private BufferedImage squishedThumb; // like thumbnail, but squished horizontally to support coverflox.
    private String caption;
    private Status status;
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    private static BufferedImage missingImage = initMissingImage();

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
        THUMB_LOADING, // loading only thumbnail
        THUMB_LOADED, // thumbnail loaded but image is not
        IMAGE_LOADING, // loading thumbnail AND image
        IMAGE_LOADED, // this also implies thumbnail is loaded
        MISSING,
        ERROR;
    }

    public WalkImage(URI uri) {
        imgURI = uri;
        if (imgURI != null) {
        uriString = uri.toString();
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

    public BufferedImage getImage() {
        if (status == Status.MISSING) {
            return missingImage;
        }
        if (im == null && status != Status.IMAGE_LOADING) {
            loadImage();
        }
        return im;
    }

    public BufferedImage getThumbnail() {
        return getThumbnail(true);
    }

    /**
     *
     * @param loadIfNeeded if false, then be reluctant to load the thumbnail.
     * @return
     */
    public BufferedImage getThumbnail(boolean loadIfNeeded) {
        switch (status) {
            case THUMB_LOADING:
                // We're already working on it in another thread
                return null;

            case THUMB_LOADED:
            case IMAGE_LOADED:
                return thumb;

            case ERROR:
                return thumb;   //TODO: May be null; use error placeholder?

            case MISSING:
                // TODO: should return a placeholder?
                return missingImage;

            case UNKNOWN:
                if(!loadIfNeeded) return null;
                setStatus(Status.THUMB_LOADING);
            //fall through
            case IMAGE_LOADING:
                if (thumb != null) return thumb;
                if (!loadIfNeeded) return null;
                //acquire thumbnail
                Runnable r = new Runnable() {

                    public void run() {
                        BufferedImage rawThumb;
                        try {
                            URI fsRoot = new URI(URISplit.parse(imgURI.toString()).path + "thumbs400");
                            //System.err.println("Thumb path: " + fsRoot);
                            FileSystem fs = FileSystem.create(fsRoot);
                            String s = imgURI.toString();
                            FileObject fo = fs.getFileObject(s.substring(s.lastIndexOf('/') + 1));

                            File localFile = fo.getFile();
                            rawThumb = ImageIO.read(localFile);

                        } catch (Exception e) {
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

                        }
                        double aspect = (double) rawThumb.getWidth() / (double) rawThumb.getHeight();

                        int height = (int) Math.round(Math.sqrt((THUMB_SIZE * THUMB_SIZE) / (aspect * aspect + 1)));
                        int width = (int) Math.round(height * aspect);

                        BufferedImageOp resizeOp = new ScalePerspectiveImageOp(rawThumb.getWidth(), rawThumb.getHeight(), 0, 0, width, height, 0, 1, 1, 0, false);
                        thumb = resizeOp.filter(rawThumb, null);
                        if (status == Status.THUMB_LOADING) {
                            setStatus(Status.THUMB_LOADED);
                        }
                    }
                };
                RequestProcessor.invokeLater(r);
                return null;
            default:
                //should never get here, but keeps Java from warning about missing return
                throw new IllegalArgumentException("Encountered invalid status in walk image.");
        } //end switch
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
        if (squishedThumb == null) {
            if (thumb == null) {
                if (getThumbnail(loadIfNeeded) == null) {
                    return null;
                }
            }

            BufferedImageOp resizeOp = new ScalePerspectiveImageOp(thumb.getWidth(), thumb.getHeight(), 0, 0, thumb.getWidth() / 10, thumb.getHeight(), 0, 1, 1, 0, false);
            squishedThumb = resizeOp.filter(thumb, null);
        }
        return squishedThumb;
    }

    private void loadImage() {
        if (status == Status.IMAGE_LOADING || status == Status.IMAGE_LOADED) {
            return;
        }
        Runnable r = new Runnable() {

            public void run() {
                try {
                    //System.err.println("download "+imgURI );

                    URI fsRoot = new URI(URISplit.parse(imgURI.toString()).path);
                    FileSystem fs = FileSystem.create(fsRoot);

                    String s = imgURI.toString();
                    FileObject fo = fs.getFileObject(s.substring(s.lastIndexOf('/') + 1));

                    File localFile = fo.getFile();

                    Thread.yield();

                    im = ImageIO.read(localFile);

                    if (thumb == null) {
                        getThumbnail(); //force thumbnail creation
                    }
                    setStatus(Status.IMAGE_LOADED);

                } catch (Exception ex) {
                    System.err.println("Error loading image file from " + imgURI.toString());
                    Logger.getLogger(WalkImage.class.getName()).log(Level.SEVERE, null, ex);
                    setStatus(Status.MISSING);
                    throw new RuntimeException(ex);
                }

            }
        };
        setStatus(Status.IMAGE_LOADING);
        RequestProcessor.invokeLater(r);
    }

    private static BufferedImage initMissingImage() {
        BufferedImage missing = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = missing.createGraphics();
        g2.setColor(java.awt.Color.RED);
        FontMetrics fm = g2.getFontMetrics(g2.getFont());
        String msg = "Missing.";
        g2.drawString(msg, (200 - fm.stringWidth(msg)) / 2, 100);

        return missing;
    }
    // Implementing the Comparable interface lets List sort
    //TODO: Compare on date information first
    public int compareTo(WalkImage o) {
        return imgURI.compareTo(o.imgURI);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        pcs.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        pcs.removePropertyChangeListener(listener);
    }
}
