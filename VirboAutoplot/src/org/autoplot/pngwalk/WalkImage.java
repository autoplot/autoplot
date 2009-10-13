package org.autoplot.pngwalk;

import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.net.URI;
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
    public static final int THUMB_SIZE=200;

    final String uriString;  // Used for sorting
    private URI imgURI;
    private BufferedImage im;
    private BufferedImage thumb;
    private String caption;
    private Status  status;
    private PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    public URI getUri() {
        return imgURI;
    }

    public enum Status {
        UNKNOWN,
        LOADING,
        LOADED,
        MISSING,
        ERROR;
    }

    public WalkImage(URI uri) {
        imgURI = uri;
        uriString = uri.toString();
        status = Status.UNKNOWN;
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
        if (im == null && status != Status.LOADING) {
            loadImage();
        }
        return im;
    }

    public BufferedImage getThumbnail() {
        // check validity of current thumbnail, i.e. it exists at correct size
        // if not valid, create it from the image

        if (thumb == null) {
            if (im == null) {
                //If the image isn't loaded, initiate loading and return.  Client
                // can listen for property change on status to send new request for thumbnail
                loadImage();
                return null;
            }

            double aspect = (double) im.getWidth() / (double) im.getHeight();

            int height = (int) Math.round(Math.sqrt((THUMB_SIZE * THUMB_SIZE) / (aspect * aspect + 1)));
            int width = (int) Math.round(height * aspect);

            BufferedImageOp resizeOp = new ScalePerspectiveImageOp(im.getWidth(), im.getHeight(), 0, 0, width, height, 0, 1, 1, 0, false);
            thumb = resizeOp.filter(im, null);
        }
        return thumb;
    }

    private void loadImage() {
        if (status == Status.LOADING) return;
        Runnable r = new Runnable() {
            public void run() {
                try {
                    //System.err.println("download "+imgURI );
                    
                    URI fsRoot = new URI(URISplit.parse(imgURI.toString()).path);
                    FileSystem fs = FileSystem.create(fsRoot);

                    String s = imgURI.toString();
                    FileObject fo = fs.getFileObject(s.substring(s.lastIndexOf('/') + 1));

                    File localFile = fo.getFile();

                    im = ImageIO.read(localFile);

                    getThumbnail(); // create the thumbnail on a separate thread as well.
                    
                    setStatus(Status.LOADED);

                } catch (Exception ex) {
                    System.err.println("Error loading image file from " + imgURI.toString());
                    Logger.getLogger(WalkImage.class.getName()).log(Level.SEVERE, null, ex);
                    setStatus(Status.MISSING);
                    throw new RuntimeException(ex);
                }

            }
        };
        setStatus(Status.LOADING);
        RequestProcessor.invokeLater(r);
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
